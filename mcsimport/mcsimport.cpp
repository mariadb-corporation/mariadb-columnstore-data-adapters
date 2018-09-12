/*
* Copyright (c) 2018 MariaDB Corporation Ab
*
* Use of this software is governed by the Business Source License included
* in the LICENSE file and at www.mariadb.com/bsl11.
*
* Change Date: 2021-10-01
*
* On the date above, in accordance with the Business Source License, use
* of this software will be governed by version 2 or later of the General
* Public License.
*/

#include <iostream>
#include <fstream>
#include <algorithm>
#include <string>
#include <sstream>
#include <vector>
#include <libmcsapi/mcsapi.h>

class InputParser {
public:
	InputParser(int &argc, char **argv) {
		for (int i = 1; i < argc; ++i)
			this->tokens.push_back(std::string(argv[i]));
	}
	const std::string& getCmdOption(const std::string &option) const {
		std::vector<std::string>::const_iterator itr;
		itr = std::find(this->tokens.begin(), this->tokens.end(), option);
		if (itr != this->tokens.end() && ++itr != this->tokens.end()) {
			return *itr;
		}
		static const std::string empty_string("");
		return empty_string;
	}
	bool cmdOptionExists(const std::string &option) const {
		return std::find(this->tokens.begin(), this->tokens.end(), option)
			!= this->tokens.end();
	}
private:
	std::vector <std::string> tokens;
};

class MCSRemoteImport {
public:
	MCSRemoteImport(std::string input_file, std::string database, std::string table, std::string mapping_file, std::string columnStoreXML, char delimiter, std::string inputDateFormat) {
		// check if we can connect to the ColumnStore database and extract the number of columns of the target table
		try {
			if (columnStoreXML == "") {
				this->driver = new mcsapi::ColumnStoreDriver();
			}
			else {
				this->driver = new mcsapi::ColumnStoreDriver(columnStoreXML);
			}
			this->cat = driver->getSystemCatalog();
			this->tab = cat.getTable(database, table);
			this->cs_table_columns = tab.getColumnCount();
			this->bulk = driver->createBulkInsert(database, table, 0, 0);
		}
		catch (mcsapi::ColumnStoreError &e) {
			std::cerr << "Error during mcsapi initialization: " << e.what() << std::endl;
			clean();
			std::exit(2);
		}

		// check if the source csv file exists and extract the number of columns of its first row
		int32_t csv_first_row_columns = -1;
		std::ifstream csvFile(input_file);
		if (!csvFile) {
			std::cerr << "Error: Can't open input file " << input_file << std::endl;
			clean();
			std::exit(2);
		}
		std::string firstLine;
		std::getline(csvFile, firstLine);
		csvFile.close();
		csv_first_row_columns = std::count(firstLine.begin(), firstLine.end(), delimiter) + 1;
		this->input_file = input_file;
		this->delimiter = delimiter;
		this->inputDateFormat = inputDateFormat;

		// check if input and output column size match if there is no explicit mapping
		if (mapping_file == "") {
			if (csv_first_row_columns == this->cs_table_columns) {
				for (int32_t x = 0; x<this->cs_table_columns; x++) {
					this->mapping.push_back(x);
				}
			}
			else {
				std::cerr << "Error: Column size of input file and target table don't match" << std::endl;
				clean();
				std::exit(2);
			}
		}
		else { // otherwise check if mapping file can be parsed and contains a valid mapping
			std::ifstream map(mapping_file);
			if (!map) {
				std::cerr << "Error: Can't open mapping file " << mapping_file << std::endl;
				clean();
				std::exit(2);
			}
			map.close();
			// try to generate the mapping
			for (int32_t col = 0; col<this->cs_table_columns; col++) {
				mcsapi::ColumnStoreSystemCatalogColumn& column = tab.getColumn(col);
				std::string colName = column.getColumnName();
				std::string mappingLine;
				std::ifstream map(mapping_file);
				bool mappingFound = false;
				while (std::getline(map, mappingLine)) {
					std::vector<std::string> splittedMappingLine = split(mappingLine, ',');
					std::string colString = std::to_string(col);
					if (splittedMappingLine[0] == colName || splittedMappingLine[0] == colString) {
						try {
							int32_t mappingValue = std::stoi(splittedMappingLine[1]);
							if (mappingValue > csv_first_row_columns) {
								std::cerr << "Warning: Mapping for ColumnStore column " << col << " (" << colName << ") is out of bounds. The input file doesn't have " << splittedMappingLine[1] << " columns" << std::endl;
							}
							else {
								std::cout << "mapping columnstore column: " << col << " with csv column: " << mappingValue << std::endl;
								this->mapping.push_back(mappingValue);
								mappingFound = true;
								break;
							}
						}
						catch (std::invalid_argument& inv) {
							std::cerr << "Warning: Mapping for ColumnStore column " << col << " (" << colName << ") isn't valid: " << splittedMappingLine[1] << std::endl;
						}
						catch (std::out_of_range& ran) {
							std::cerr << "Warning: Mapping for ColumnStore column " << col << " (" << colName << ") is out of bounds. " << splittedMappingLine[1] << std::endl;
						}
					}
				}
				if (!mappingFound) {
					std::cerr << "Error: No mapping found for ColumnStore column " << col << ": " << colName << std::endl;
					clean();
					std::exit(2);
				}
			}
		}
	}
	int32_t import() {
		try {
			std::ifstream csvFile(this->input_file);
			std::string rowLine;
			while (std::getline(csvFile, rowLine)) {
				//remove '/r' line ending from Windows files
				if (rowLine.size() && rowLine[rowLine.size() - 1] == '\r') {
					rowLine = rowLine.substr(0, rowLine.size() - 1);
				}
				std::vector<std::string> splittedRowLine = split(rowLine, this->delimiter);
				for (int32_t col = 0; col<this->cs_table_columns; col++) {
					int32_t csvColumn = this->mapping[col];
					if (csvColumn <= splittedRowLine.size()-1 && (std::string) splittedRowLine[csvColumn] != "") { //last rowLine entry could be NULL and therefore not in the vector
						// if an input date format is specified and the target column is of type DATE or DATETIME, transform the input to ColumnStoreDateTime and inject it
						if (this->inputDateFormat != "" && (tab.getColumn(col).getType() == mcsapi::DATA_TYPE_DATE || tab.getColumn(col).getType() == mcsapi::DATA_TYPE_DATETIME)) {
							mcsapi::ColumnStoreDateTime dt = mcsapi::ColumnStoreDateTime((std::string) splittedRowLine[csvColumn],this->inputDateFormat);
							bulk->setColumn(col, dt);
						} else { // otherwise just inject the plain value as string
							bulk->setColumn(col, (std::string) splittedRowLine[csvColumn]);
						}
					} else {
						bulk->setNull(col);
					}
				}
				bulk->writeRow();
			}
			bulk->commit();
		}
		catch (std::exception& e) {
			std::cerr << "Error during mcsapi bulk operation: " << e.what() << std::endl;
			bulk->rollback();
			std::cerr << "Rollback performed." << std::endl;
			clean();
			return 3;
		}
		mcsapi::ColumnStoreSummary& sum = bulk->getSummary();
		std::cout << "Execution time: " << sum.getExecutionTime() << "s" << std::endl;
		std::cout << "Rows inserted: " << sum.getRowsInsertedCount() << std::endl;
		std::cout << "Truncation count: " << sum.getTruncationCount() << std::endl;
		std::cout << "Saturated count: " << sum.getSaturatedCount() << std::endl;
		std::cout << "Invalid count: " << sum.getInvalidCount() << std::endl;

		clean();
		return 0;
	}
private:
	mcsapi::ColumnStoreDriver* driver = nullptr;
	mcsapi::ColumnStoreBulkInsert* bulk = nullptr;
	mcsapi::ColumnStoreSystemCatalog cat;
	mcsapi::ColumnStoreSystemCatalogTable tab;
	std::string input_file;
	std::string inputDateFormat;
	char delimiter;
	int32_t cs_table_columns = -1;
	std::vector <int32_t> mapping;  //mapping[nr] returns the csv file column id as mapping for the columnstore column nr

	template<typename Out>
	void split(const std::string &s, char delim, Out result) {
		std::stringstream ss(s);
		std::string item;
		while (std::getline(ss, item, delim)) {
			*(result++) = item;
		}
	}

	std::vector<std::string> split(const std::string &s, char delim) {
		std::vector<std::string> elems;
		split(s, delim, std::back_inserter(elems));
		return elems;
	}

	void clean() {
		delete this->bulk;
		delete this->driver;
	}
};

int main(int argc, char* argv[])
{
	// Check if the command line arguments are valid
	if (argc < 4) {
		std::cerr << "Usage: " << argv[0] << " input_file database table [-m mapping_file] [-c Columnstore.xml] [-d delimiter] [-df date_format]" << std::endl;
		return 1;
	}

	// Parse the optional command line arguments
	InputParser input(argc, argv);
	std::string mappingFile;
	std::string columnStoreXML;
	std::string inputDateFormat;
	char delimiter = ',';
	if (input.cmdOptionExists("-m")) {
		mappingFile = input.getCmdOption("-m");
	}
	if (input.cmdOptionExists("-c")) {
		columnStoreXML = input.getCmdOption("-c");
	}
	if (input.cmdOptionExists("-d")) {
		std::string delimiterString = input.getCmdOption("-d");
		if (delimiterString.length() != 1) {
			std::cerr << "Error: Delimiter needs to be one character. Current length: " << delimiterString.length() << std::endl;
			return 2;
		}
		delimiter = delimiterString[0];
	}
	if (input.cmdOptionExists("-df")) {
		inputDateFormat = input.getCmdOption("-df");
	}
	MCSRemoteImport* mcsimport = new MCSRemoteImport(argv[1], argv[2], argv[3], mappingFile, columnStoreXML, delimiter, inputDateFormat);
	int32_t rtn = mcsimport->import();
	return rtn;
}
