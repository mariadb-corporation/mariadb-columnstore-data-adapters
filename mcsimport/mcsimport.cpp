/*
* Copyright (c) 2018 MariaDB Corporation Ab
*
* Use of this software is governed by the Business Source License included
* in the LICENSE file and at www.mariadb.com/bsl11.
*
* Change Date: 2021-12-01
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
#include <map>
#include <libmcsapi/mcsapi.h>
#include <yaml-cpp/yaml.h>

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
	MCSRemoteImport(std::string input_file, std::string database, std::string table, std::string mapping_file, std::string columnStoreXML, char delimiter, std::string inputDateFormat, bool default_non_mapped) {
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
		std::ifstream csvFile(input_file);
		if (!csvFile) {
			std::cerr << "Error: Can't open input file " << input_file << std::endl;
			clean();
			std::exit(2);
		}
		std::string firstLine;
		std::getline(csvFile, firstLine);
		csvFile.close();
		int32_t csv_first_row_number_of_columns = std::count(firstLine.begin(), firstLine.end(), delimiter) + 1;
		this->input_file = input_file;
		this->delimiter = delimiter;
		this->inputDateFormat = inputDateFormat;

		if (mapping_file == "") {// if no mapping file was provided use implicit mapping of columnstore_column to csv_column
			generateImplicitMapping(csv_first_row_number_of_columns, default_non_mapped);
		}
		else { // if a mapping file was provided infer the mapping from the mapping file
			generateExplicitMapping(csv_first_row_number_of_columns, default_non_mapped, mapping_file);
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
				for (int32_t col = 0; col < this->cs_table_columns; col++) {
					int32_t csvColumn = this->mapping[col];
					if (csvColumn == CUSTOM_DEFAULT_VALUE || csvColumn == COLUMNSTORE_DEFAULT_VALUE) {
						if (customDefaultValue[col] == "" && this->tab.getColumn(col).isNullable()) {
							bulk->setNull(col);
						} else{
							bulk->setColumn(col, customDefaultValue[col]);
						}
					}
					else if (csvColumn <= splittedRowLine.size() - 1 && (std::string) splittedRowLine[csvColumn] != "") { //last rowLine entry could be NULL and therefore not in the vector
						// if an (custom) input date format is specified and the target column is of type DATE or DATETIME, transform the input to ColumnStoreDateTime and inject it
						if ((this->customInputDateFormat.find(col) != this->customInputDateFormat.end() || this->inputDateFormat != "") && (tab.getColumn(col).getType() == mcsapi::DATA_TYPE_DATE || tab.getColumn(col).getType() == mcsapi::DATA_TYPE_DATETIME)) {
							if (this->customInputDateFormat.find(col) != this->customInputDateFormat.end()) {
								mcsapi::ColumnStoreDateTime dt = mcsapi::ColumnStoreDateTime((std::string) splittedRowLine[csvColumn], this->customInputDateFormat[col]);
								bulk->setColumn(col, dt);
							}
							else {
								mcsapi::ColumnStoreDateTime dt = mcsapi::ColumnStoreDateTime((std::string) splittedRowLine[csvColumn], this->inputDateFormat);
								bulk->setColumn(col, dt);
							}
						}
						else { // otherwise just inject the plain value as string
							bulk->setColumn(col, (std::string) splittedRowLine[csvColumn]);
						}
					}
					else 
					{
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
	enum mapping_codes {COLUMNSTORE_DEFAULT_VALUE=-1, CUSTOM_DEFAULT_VALUE=-2};
	std::map<int32_t, int32_t> mapping; // columnstore_column #, csv_column # or item of mapping_codes
	std::map<int32_t, std::string> customInputDateFormat; //columnstore_column #, csv_input_date_format
	std::map<int32_t, std::string> customDefaultValue; // columnstore_column #, custom_default_value

	void generateImplicitMapping(int32_t csv_first_row_number_of_columns, bool default_non_mapped) {
		// check the column sizes of csv input and columnstore target for compatibility
		if (csv_first_row_number_of_columns < this->cs_table_columns && !default_non_mapped) {
			std::cerr << "Error: Column size of input file is less than the column size of the target table" << std::endl;
			clean();
			std::exit(2);
		}
		else if (csv_first_row_number_of_columns < this->cs_table_columns && default_non_mapped){
			std::cout << "Warning: Column size of input file is less than the column size of the target table." << std::endl;
			std::cout << "Default values will be used for non mapped columnstore columns." << std::endl;
		}

		if (csv_first_row_number_of_columns > this->cs_table_columns) {
			std::cout << "Warning: Column size of input file is higher than the column size of the target table." << std::endl;
			std::cout << "Remaining csv columns won't be injected." << std::endl;
		}

		// generate the mapping
		for (int32_t x = 0; x < this->cs_table_columns; x++) {
			if (x < csv_first_row_number_of_columns) {
				this->mapping[x] = x;
			}
			else { // map to the default value
				this->mapping[x] = this->COLUMNSTORE_DEFAULT_VALUE;
				this->customDefaultValue[x] = this->tab.getColumn(x).getDefaultValue();
			}
		}
	}

	void generateExplicitMapping(int32_t csv_first_row_number_of_columns, bool default_non_mapped, std::string mapping_file) {

		// check if the mapping file exists
		std::ifstream map(mapping_file);
		if (!map) {
			std::cerr << "Error: Can't open mapping file " << mapping_file << std::endl;
			clean();
			std::exit(2);
		}
		map.close();

		// check if the yaml file is parseable
		YAML::Node yaml;
		try {
			yaml = YAML::LoadFile(mapping_file);
		}
		catch (YAML::ParserException& e) {
			std::cerr << "Error: Mapping file " << mapping_file << " couldn't be parsed." << std::endl << e.what() << std::endl;
			clean();
			std::exit(2);
		}

		// generate the mapping
		try {
			int32_t csv_column_counter = 0;
			for (std::size_t i = 0; i < yaml.size(); i++) {
				YAML::Node entry = yaml[i];
				// handling of the column definition expressions
				if (entry["column"]) {
					int32_t csv_column = -1;
					if (entry["column"].IsNull()) { // no explicit column number was given, use the implicit from csv_column_counter
						csv_column = csv_column_counter;
						csv_column_counter++;
					}
					else if (entry["column"].IsSequence()) { //ignore scalar
						csv_column_counter++;
					}
					else if (entry["column"].IsDefined()) { // an explicit column number was given
						csv_column = entry["column"].as<std::int32_t>();
					}
					// handle the mapping in non-ignore case
					if (csv_column >= 0) {
						// check if the specified csv column is valid
						if (csv_column >= csv_first_row_number_of_columns) {
							std::cerr << "Warning: Specified source column " << csv_column << " is out of bounds.  This mapping will be ignored." << std::endl;
						}
						// check if the specified target is valid
						else if(!entry["target"]){
							std::cerr << "Warning: No target column specified for source column " << csv_column << ". This mapping will be ignored." << std::endl;
						} 
						else if (getTargetId(entry["target"].as<std::string>()) < 0) {
							std::cerr << "Warning: Specified target column " << entry["target"] << " could not be found. This mapping will be ignored." << std::endl;
						} // if all tests pass, do the mapping
						else {
							int32_t targetId = getTargetId(entry["target"].as<std::string>());
							if (this->mapping.find(targetId) != this->mapping.end()) {
								std::cerr << "Warning: Already existing mapping for source column " << mapping[targetId] << " mapped to ColumnStore column " << targetId << " is overwritten by new mapping." << std::endl;
							}
							this->mapping[targetId] = csv_column;
							handleOptionalColumnParameter(csv_column, targetId, entry);
						}
					}
				}
				// handling of the target definition expressions
				else if (entry["target"] && entry["target"].IsDefined()) { //target default value configuration
					//check if the specified target is valid
					if (getTargetId(entry["target"].as<std::string>()) < 0) {
						std::cerr << "Warning: Specified target column " << entry["target"] << " could not be found. This target default value definition will be ignored." << std::endl;
					}
					// check if there is a default value defined
					else if (!(entry["value"] && entry["value"].IsDefined())) {
						std::cerr << "Warning: No default value specified for target column " << entry["target"] << ". This target default value definition will be ignored." << std::endl;
					}
					// if all tests pass, do the parsing
					else {
						std::int32_t targetId = getTargetId(entry["target"].as<std::string>());
						if (this->mapping.find(targetId) != this->mapping.end()) {
							std::cerr << "Warning: Already existing mapping for source column " << mapping[targetId] << " mapped to ColumnStore column " << targetId << " is overwritten by new default value." << std::endl;
						}
						if (entry["value"].as<std::string>() == "default") {
							this->mapping[targetId] = COLUMNSTORE_DEFAULT_VALUE;
							this->customDefaultValue[targetId] = this->tab.getColumn(targetId).getDefaultValue();
						}
						else {
							this->mapping[targetId] = CUSTOM_DEFAULT_VALUE;
							this->customDefaultValue[targetId] = entry["value"].as<std::string>();
						}
					}
				}
				else {
					std::cerr << "Warning: Defined expression " << entry << " is not supported and will be ignored." << std::endl;
				}
			}
		}
		catch (std::exception& e) {
			std::cerr << "Error: Explicit mapping couldn't be generated. " << e.what() << std::endl;
			clean();
 			std::exit(2);
		}

		// check if the mapping is valid and apply missing defaults if default_non_map was chosen
		for (int32_t col = 0; col < this->cs_table_columns; col++) {
			if (this->mapping.find(col) == this->mapping.end()) {
				if (default_non_mapped) {
					this->mapping[col] = COLUMNSTORE_DEFAULT_VALUE;
					this->customDefaultValue[col] = this->tab.getColumn(col).getDefaultValue();
					std::cout << "Notice: Using default value for ColumnStore column " << col << ": " << this->tab.getColumn(col).getColumnName() << std::endl;
				}
				else {
					std::cerr << "Error: No mapping found for ColumnStore column " << col << ": " << this->tab.getColumn(col).getColumnName() << std::endl;
					clean();
					std::exit(2);
				}
			}
		}
	}

	void handleOptionalColumnParameter(int32_t source, int32_t target, YAML::Node column) {
		// if there is already an old custom input date format entry delete it
		if (this->customInputDateFormat.find(target) != this->customInputDateFormat.end()) {
			this->customInputDateFormat.erase(target);
		}

		// set new custom input date format if applicable
		if (column["format"] && (this->tab.getColumn(target).getType() == mcsapi::DATA_TYPE_DATE || this->tab.getColumn(target).getType() == mcsapi::DATA_TYPE_DATETIME)) {
			//remove annotation marks from received custom date format
			std::string df = column["format"].as<std::string>();
			if (df[0] == '"' && df[df.size() - 1] == '"') {
				df = df.substr(1, df.size() - 1);
			}
			this->customInputDateFormat[target] = df;
		}
	}

	/*
	* returns the target id of given string's columnstore representation if it can be found. otherwise -1.
	*/
	int32_t getTargetId(std::string target) {
		try {
			int32_t targetId = std::stoi(target);
			this->tab.getColumn(targetId);
			return targetId;
		}
		catch (std::exception&) { }

		try {
			int32_t targetId = this->tab.getColumn(target).getPosition();
			return targetId;
		}
		catch (std::exception&) { }

		return -1;
	}

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
		std::cerr << "Usage: " << argv[0] << " database table input_file [-m mapping_file] [-c Columnstore.xml] [-d delimiter] [-df date_format] [-default_non_mapped]" << std::endl;
		return 1;
	}

	// Parse the optional command line arguments
	InputParser input(argc, argv);
	std::string mappingFile;
	std::string columnStoreXML;
	std::string inputDateFormat;
	bool default_non_mapped = false;
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
	if (input.cmdOptionExists("-default_non_mapped")) {
		default_non_mapped = true;
	}
	MCSRemoteImport* mcsimport = new MCSRemoteImport(argv[3], argv[1], argv[2], mappingFile, columnStoreXML, delimiter, inputDateFormat, default_non_mapped);
	int32_t rtn = mcsimport->import();
	return rtn;
}

