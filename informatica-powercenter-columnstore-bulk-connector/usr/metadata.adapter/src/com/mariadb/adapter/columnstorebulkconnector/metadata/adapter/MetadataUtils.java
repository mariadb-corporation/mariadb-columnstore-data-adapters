package com.mariadb.adapter.columnstorebulkconnector.metadata.adapter;

import java.util.List;
import com.informatica.sdk.adapter.metadata.common.Option;
import com.informatica.sdk.adapter.metadata.common.CCatalogImportOpts;
import com.informatica.sdk.adapter.metadata.patternblocks.container.semantic.iface.Package;

public class MetadataUtils  {
    public static String getNameFilter(List<Option> options) {
    	String nameFilter = (String)getOption(options,CCatalogImportOpts.FILTER_NMO_NAME);
    	/* If name filter is not to be used, set it to null */
    	if (nameFilter != null
    			&& (nameFilter.isEmpty() || nameFilter.equals("*"))) {
    		nameFilter = null;
    	}
    	return nameFilter;
    }

    public static Package getStartFolder(List<Option> options) {
    	Object sdkStartFolder = getOption(options,CCatalogImportOpts.START_AT);
    	return (Package)sdkStartFolder;
    }


    public static String getPathFilter(List<Option> options) {
    	return (String)getOption(options,CCatalogImportOpts.FILTER_BY_PATH);
    }

    public static String getNmoDescFilter(List<Option> options) {
    	return (String)getOption(options,CCatalogImportOpts.FILTER_NMO_DESC);
    }

    public static String getNmoTypeFilter(List<Option> options) {
    	return (String)getOption(options,CCatalogImportOpts.FILTER_BY_NMO_TYPE);
    }

    public static Boolean getSkipDescription(List<Option> options) {
    	return (Boolean)getOption(options,CCatalogImportOpts.SKIP_DESCRIPTIONS);
    }

    public static Object getOption(List<Option> options,int option) {
    	for (Option opt : options) {
    		int optionID = opt.getDescription().getEffectiveDefinition()
    				.getOptionID();
    		if (optionID == option) {
    			return  opt.getValue();
    		} 
    	}
    	return null;
    }

     public static String getPattern(String nmoName) {
     		String pattern = null;

       		if(nmoName.equals("table")){
       		 		pattern = "TypeA";
       			}

       		if(pattern == "TypeA"){
       			return "Record";
       		}else if(pattern == "TypeC"){
       			return "Procedure";
       		}
    		return null;
       }

}