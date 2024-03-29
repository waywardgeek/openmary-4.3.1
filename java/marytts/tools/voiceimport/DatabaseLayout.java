/**
 * Copyright 2007 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * This file is part of MARY TTS.
 *
 * MARY TTS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package marytts.tools.voiceimport;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import marytts.Version;
import marytts.util.MaryUtils;

/**
 * The DatabaseLayout class registers the base directory of a voice database,
 * as well as the various subdirectories where the various voice database
 * components should be stored or read from.
 * 
 * @author Anna Hunecke
 *
 */
public class DatabaseLayout 
{   
    private String configFileName;
    private SortedMap<String,String> props;
    private BasenameList bnl;
    private SortedMap<String,SortedMap<String,String>> localProps;
    private SortedMap<String,String> external;   /* paths for external binaries used in HMM voice creation */
    private String fileSeparator;
    private VoiceImportComponent[] components;
    private String[] compNames;
    private Map<String,VoiceImportComponent> compnames2comps;
    private SortedMap<String,Object/*either String or SortedMap<String,String>*/> missingProps;
    private String missingPropsHelp;
    private boolean initialized;
    private List<String> uneditableProps;
    private Map<String,String> props2Help;
    //marybase
    public final String MARYBASE = "db.marybase";
    //marybase version
    public final String MARYBASEVERSION = "db.marybaseversion";
    //voicename
    public final String VOICENAME = "db.voicename";
    //gender
    public final String GENDER = "db.gender";
    //domain
    public final String DOMAIN  = "db.domain";
    //locale
    public final String LOCALE = "db.locale";
    //the sampling rate
    public final String SAMPLINGRATE = "db.samplingrate";
    //root directory for the database
    public final String ROOTDIR = "db.rootDir";        
    //directory for Mary config files
    public final String CONFIGDIR = "db.configDir";
    //directory for Mary voice files
    public final String FILEDIR = "db.fileDir";
    //mary file extension
    public final String MARYEXT = "db.maryExtension";  
    //basename list file
    public final String BASENAMEFILE = "db.basenameFile";
    //text file dir
    public final String TEXTDIR = "db.textDir";
    //text file extension
    public final String TEXTEXT = "db.textExtension";  
    //wav file dir
    public final String WAVDIR = "db.wavDir";
    //wav file extension
    public final String WAVEXT = "db.wavExtension";
    //phonetic label files
    public final String LABDIR = "db.labDir";
    //phonetic label file extension
    public final String LABEXT = "db.labExtension";
    //pitchmark file dir
    public final String PMDIR = "db.pmDir";
    //pitchmark file extension
    public final String PMEXT = "db.pmExtension";
    //pitch file dir
    public final String PTCDIR = "db.ptcDir";
    //pitch file extension
    public final String PTCEXT = "db.ptcExtension";
    //directory for temporary files
    public final String TEMPDIR = "db.tempDir";
    //maryxml dir
    public final String MARYXMLDIR = "db.maryxmlDir";
    //maryxml extentsion
    public final String MARYXMLEXT = "db.maryxmlExtension";
    // Prompt allophones dir
    public final String PROMPTALLOPHONESDIR = "db.promptAllophonesDir";
    // Allophones aligned with labels
    public final String ALLOPHONESDIR = "db.allophonesDir";
    // Allophone set definition file
    public final String ALLOPHONESET = "db.allophoneSet";
    public String MARYSERVERHOST = "db.maryServerHost";
    public String MARYSERVERPORT = "db.maryServerPort";
    public String PHONELABDIR = "db.phoneLabDir";
    public String PHONEFEATUREDIR = "db.phoneFeatureDir";
    public String HALFPHONELABDIR = "db.halfphoneLabDir";
    public String HALFPHONEFEATUREDIR = "db.halfphoneFeatureDir";
    
    public String VOCALIZATIONSDIR = "db.vocalizationsDir";
    
    // paths used in HMM voice creation
    public String AWKPATH       = "external.awkPath";
    public String PERLPATH      = "external.perlPath";
    public String BCPATH        = "external.bcPath";
    public String HTSPATH       = "external.htsPath";
    public String HTSENGINEPATH = "external.htsEnginePath";
    public String SPTKPATH      = "external.sptkPath";
    public String TCLPATH       = "external.tclPath";
    public String SOXPATH       = "external.soxPath";
    public String EHMMPATH      = "external.ehmmPath";

    public DatabaseLayout()
    throws Exception
    {
        initialized = false;
        initialize(new VoiceImportComponent[0]);
    }
    
    public DatabaseLayout(VoiceImportComponent[] comps)
    throws Exception
    {        
        initialized = false;
        initialize(comps);
    }
    
    public DatabaseLayout(VoiceImportComponent comp)
    throws Exception
    {        
        initialized = false;
        VoiceImportComponent[] comps = new VoiceImportComponent[1];
        comps[0] = comp;
        initialize(comps);        
    }
    
    private void setupHelp()
    {
        props2Help = new TreeMap<String, String>();
        props2Help.put(BASENAMEFILE,"file containing the list of files that are used to build the voice");
        props2Help.put(DOMAIN,"general or limited");
        props2Help.put(GENDER,"female or male");
        props2Help.put(LABDIR,"directory containing the label files. Will be created if it does not exist.");
        props2Help.put(LABEXT,"extension of the label files, default: \".lab\"");
        props2Help.put(LOCALE,"de, en or en_US");
        props2Help.put(MARYBASE,"directory containing the local Mary installation");
        props2Help.put(MARYBASEVERSION,"local Mary installation version");
        props2Help.put(MARYXMLDIR,"directory containing maryxml representations of the transcripts. Will be created if it does not exist.");
        props2Help.put(MARYXMLEXT,"extension of the maryxml files, default: \".xml\"");
        props2Help.put(ROOTDIR,"directory in which all the files created during installation will be stored. Will be created if it does not exist.");
        props2Help.put(SAMPLINGRATE,"the sampling rate of the wave files, default: \"16000\"");
        props2Help.put(TEXTDIR,"directory containing the transcript files. Will be created if it does not exist.");
        props2Help.put(TEXTEXT,"extension of the transcript files, default: \".txt\"");
        props2Help.put(VOICENAME,"the name of the voice, one word, for example: \"my_voice\"");
        props2Help.put(WAVDIR,"directory containing the wave files. If it does not exist, an Error is thrown.");
        props2Help.put(PMDIR,"directory containing the pitchmark files. If it does not exist, an Error is thrown.");
        props2Help.put(PTCDIR,"directory containing the pitch files. If it does not exist, an Error is thrown.");
        props2Help.put(PROMPTALLOPHONESDIR, "directory containing the allophones files predicted by mary");
        props2Help.put(ALLOPHONESDIR, "directory containing allophones files aligned with (possibly manually corrected) labels");
        props2Help.put(MARYSERVERHOST, "hostname of the MARY TTS server running NLP components for this language");
        props2Help.put(MARYSERVERPORT, "port of the MARY TTS server running NLP components for this language");
        props2Help.put(PHONEFEATUREDIR, "directory containing the phone features.");
        props2Help.put(PHONELABDIR, "directory containing the phone unit labels");
        props2Help.put(HALFPHONEFEATUREDIR, "directory containing the half-phone features.");
        props2Help.put(HALFPHONELABDIR, "directory containing the half-phone unit labels");
        props2Help.put(VOCALIZATIONSDIR, "directory in which all files created during listener vocal behavior creation");
        for (int i=0;i<components.length;i++) {
            components[i].setupHelp();
        }
    }
    
    
    private void initialize(VoiceImportComponent[] theComponents)
    throws Exception
    {
        System.out.println("Loading database layout:");
        
        /* first, handle the components */     
        this.components = theComponents;      
        getCompNames();  
        /* initialize the help texts */
        setupHelp();
        
        fileSeparator = System.getProperty("file.separator");
        /* define the uneditable props */
        uneditableProps = new ArrayList<String>();
        uneditableProps.add(MARYEXT);
        uneditableProps.add(CONFIGDIR);
        uneditableProps.add(FILEDIR);
        uneditableProps.add(TEMPDIR);
        uneditableProps.add(WAVEXT);
        
        /* check if there is a config file */ 
        configFileName = System.getProperty("user.dir")+System.getProperty("file.separator")+"database.config";
        File configFile = new File(configFileName);
        if (configFile.exists()) {
            
            System.out.println("Reading config file "+configFileName);
            readConfigFile(configFile);  
            SortedMap<String,String> defaultGlobalProps = new TreeMap<String,String>();
            //get the default values for the global props
            defaultGlobalProps = initDefaultProps(defaultGlobalProps,true);   
            
            /* if there is a valid marybase, then check if there is an externalBinaries.config and load the paths 
             * the paths will be loaded in external */
            String externalConfigFileName = getProp(MARYBASE) + "/lib/external/externalBinaries.config";
            File externalConfigFile = new File(externalConfigFileName);
            if (externalConfigFile.exists()) {            
                System.out.println("Reading external binaries config file " + externalConfigFile);
                readExternalBinariesConfigFile(externalConfigFile); 
            }
            
            
            //get the local default props from the components
            SortedMap<String,SortedMap<String,String>> defaultLocalProps = getDefaultPropsFromComps();
            //try to get all props and values from config file
            if (!checkProps(defaultGlobalProps, defaultLocalProps)) {
                //some props are missing                
                //prompt the user for the missing props
                //(user input updates the props via the GUI)
                if (!displayProps(missingProps,missingPropsHelp,"The following properties are missing:"))
                    return;
                //check if all dirs have a file separator at the end
                checkForFileSeparators();
                //save the props
                saveProps(configFile);
            } 
            //check if all dirs have a file separator at the end
            checkForFileSeparators();
                        
        } else {
            //we have no values for our props
            props = new TreeMap<String, String>();
            //prompt the user for some props
            if (!promptUserForBasicProps(props))
                return;
            //fill in the other props with default values
            props = initDefaultProps(props,false);
            
            /* if there is a valid marybase, then check if there is an externalBinaries.config and load the paths 
             * the paths will be load in external */
            String externalConfigFileName = getProp(MARYBASE) + "/lib/external/externalBinaries.config";
            File externalConfigFile = new File(externalConfigFileName);
            if (externalConfigFile.exists()) {            
                System.out.println("Reading external binaries config file " + externalConfigFile);
                readExternalBinariesConfigFile(externalConfigFile); 
            }
            
            
            //get the local default props from the components
            localProps = getDefaultPropsFromComps();
            //check if all dirs have a file separator at the end
            checkForFileSeparators();
            //save the props
            saveProps(configFile);
        }
        assureFileIntegrity();
        loadBasenameList();        
        initializeComps();
        
        initialized = true;
    }
    
    
     /**
     * Get the names of the components
     * and store them in array
     */
    private void getCompNames()
    {
        compnames2comps = new HashMap<String, VoiceImportComponent>();
        compNames = new String[components.length];
        for (int i=0;i<components.length;i++){
            compNames[i] = components[i].getName();
            compnames2comps.put(compNames[i],components[i]);
        }        
    }
    
    /**
     * Obtain a voice import component by its name. This can be used to run invidivual functions of a given voice import component from another component.
     * Handle with care -- only use this if you know what you are doing!
     * @param componentName
     * @return the named voice import component, or null if there is no such component.
     */
    public VoiceImportComponent getComponent(String componentName) {
        return compnames2comps.get(componentName);
    }

    
    /**
     * Read the props in the config file
     * @param configFile the config file
     */
    private void readConfigFile(File configFile)
    throws IOException
    {
          props = new TreeMap<String, String>();
          localProps = new TreeMap<String, SortedMap<String,String>>();
        
        try{
            //open the file
            BufferedReader in = new BufferedReader(new InputStreamReader(
                                  new FileInputStream(configFile),"UTF-8"));
            String line;
            while ((line=in.readLine())!= null) {
                if (line.startsWith("#") || line.trim().equals(""))
                    continue;
                //System.out.println(line);
                //line looks like "<propName> <value>"
                //<propname> looks like "<compName>.<prop>"
                String[] lineSplit = line.split(" ", 2);
                if (lineSplit[0].startsWith("db.")) {
                   //global prop
                    props.put(lineSplit[0],lineSplit[1]);                    
                } 
                else {
                    //local prop
                    String compName = lineSplit[0].substring(0,lineSplit[0].indexOf('.'));
                    if (localProps.containsKey(compName)) {
                        SortedMap<String,String> localPropMap = localProps.get(compName);
                        localPropMap.put(lineSplit[0],lineSplit[1]);
                    } else {
                        SortedMap<String,String> localPropMap = new TreeMap<String, String>();
                        localPropMap.put(lineSplit[0],lineSplit[1]);
                        localProps.put(compName,localPropMap);
                    }       
                }
            }
            in.close(); 
            //add the props that are not editable
            SortedMap<String,String> defaultGlobalProps = new TreeMap<String, String>();
            //get the default values for the global props
            defaultGlobalProps = initDefaultProps(defaultGlobalProps, false);
            for (Iterator<String> it=uneditableProps.iterator(); it.hasNext();) {
              String key = it.next();
              if (defaultGlobalProps.containsKey(key)) {
                  props.put(key, defaultGlobalProps.get(key));
              } else {
                  //this case should never happen
                  throw new IllegalStateException("Uneditable global prop "+key
                          +" not defined in default props.");
              }
            }            
        } catch (IOException e) {
            IOException myIOE = new IOException("Error reading config file");
            myIOE.initCause(e);
            throw myIOE;
        }   
    }
    
   
    /**
     * Read the props in the config file
     * @param configFile the config file
     */
    private void readExternalBinariesConfigFile(File configFile)
    throws IOException
    {
        external = new TreeMap<String, String>();
        
        try{
            //open the file
            BufferedReader in = new BufferedReader(new InputStreamReader(
                                  new FileInputStream(configFile),"UTF-8"));
            String line;
            while ((line=in.readLine())!= null) {
                if (line.startsWith("#") || line.trim().equals(""))
                    continue;
                //System.out.println(line);
                //line looks like "<propName> <value>"
                //<propname> looks like "<compName>.<prop>"
                String[] lineSplit = line.split(" ", 2);
                if (lineSplit[0].startsWith("external.")) {
                    //global external
                     external.put(lineSplit[0],lineSplit[1]);                    
                }
            }
            in.close();             
        } catch (IOException e) {
            IOException myIOE = new IOException("Error reading config file");
            myIOE.initCause(e);
            throw myIOE;
        }   
    }
    
    
    /**
     * Check if all props are set
     * @param defaultGlobalProps default global props
     * @param defaultLocalProps default local props
     * @return true if all props are set, false otherwise
     */
    private boolean checkProps(SortedMap<String,String> defaultGlobalProps,SortedMap<String,SortedMap<String,String>> defaultLocalProps){
        boolean allFine = true;
        missingProps = new TreeMap<String, Object>();
        StringBuilder helpTextBuf = new StringBuilder();
        helpTextBuf.append("<html>\n<head>\n<title>SETTINGS HELP</title>\n"
                +"</head>\n<body>\n<dl>\n");
        /* check the local props */
        Set<String> defaultProps = defaultLocalProps.keySet();
        for (Iterator<String> it = defaultProps.iterator();it.hasNext();){
            String key = it.next();
            if (!localProps.containsKey(key)){
                VoiceImportComponent comp = (VoiceImportComponent) compnames2comps.get(key);
                SortedMap<String,String> nextLocalPropMap = defaultLocalProps.get(key);
                if (nextLocalPropMap != null && !nextLocalPropMap.isEmpty()) {
                    missingProps.put(key,nextLocalPropMap);
                    for (Iterator<String> it2=nextLocalPropMap.keySet().iterator();it2.hasNext();){
                        String nextKey = it2.next();                    
                        helpTextBuf.append("<dt><strong>"+nextKey+"</strong></dt>\n"
                                +"<dd>"+comp.getHelpTextForProp(nextKey)+"</dd>\n");
                    }
                    allFine = false;
                }
            } else {
                VoiceImportComponent comp = compnames2comps.get(key);
                SortedMap<String,String> nextLocalPropMap = localProps.get(key);
                SortedMap<String,String> nextDefaultLocalPropMap = defaultLocalProps.get(key);
                Set<String> nextDefaultLocalProps = nextDefaultLocalPropMap.keySet();
                boolean haveAllLocalProps = true;
                SortedMap<String,String> missingLocalPropMap = new TreeMap<String, String>();
                for (Iterator<String> it2 = nextDefaultLocalProps.iterator();it2.hasNext();){
                    String nextKey = it2.next();
                    if (!nextLocalPropMap.containsKey(nextKey)){
                        missingLocalPropMap.put(nextKey,nextDefaultLocalPropMap.get(nextKey));
                        helpTextBuf.append("<dt><strong>"+nextKey+"</strong></dt>\n"
                                +"<dd>"+comp.getHelpTextForProp(nextKey)+"</dd>\n");
                        haveAllLocalProps = false;
                    }else {
                        //make sure all dir names have a / at the end
                        // TODO verify that this way of deciding whether a key represents a path is really robust,
                        // also consider unifying the numerous condition blocks like this into one function for maintainability 
                        if (nextKey.endsWith("Dir")){
                            String prop = nextLocalPropMap.get(nextKey);
                            if (!prop.endsWith(fileSeparator)){
                                prop = prop+fileSeparator;
                                nextLocalPropMap.put(nextKey,prop);
                            }
                        }
                    }
                }
                if (!haveAllLocalProps){
                    missingProps.put(key,missingLocalPropMap);
                    allFine = false;
                }
            }
        }
        /* check the global props */        
        defaultProps = defaultGlobalProps.keySet();
        for (Iterator<String> it = defaultProps.iterator();it.hasNext();){
            String key = it.next();
            if (!props.containsKey(key)){
                missingProps.put(key,defaultGlobalProps.get(key));
                 helpTextBuf.append("<dt><strong>"+key+"</strong></dt>\n"
                    +"<dd>"+props2Help.get(key)+"</dd>\n");
                allFine = false;
            } else {
                //make sure all dir names have a / at the end
                if (key.endsWith("Dir")){
                    String prop = (String)props.get(key);
                    if (!prop.endsWith(fileSeparator)){
                        prop = prop+fileSeparator;
                        props.put(key,prop);
                    }
                }
            }
            
        }
        helpTextBuf.append("</dl>\n</body>\n</html>");
        missingPropsHelp = helpTextBuf.toString();
        return allFine;
    }    
    
    
    private void checkForFileSeparators(){
        
        /* check the global props */       
        Set<String> propKeys = props.keySet();
        for (Iterator<String> it = propKeys.iterator();it.hasNext();){
            String key = it.next();
            //make sure all dir names have a / at the end
            if (key.endsWith("Dir")){
                String prop = (String)props.get(key);
                char lastChar = prop.charAt(prop.length()-1);
                if (Character.isLetterOrDigit(lastChar)){
                    props.put(key,prop+fileSeparator);
                }
            }              
        }
        /* check the local props */
        Set<String> localPropKeys = localProps.keySet();
        for (Iterator<String> it = localPropKeys.iterator();it.hasNext();){
            SortedMap<String,String> nextLocalPropMap = localProps.get(it.next());
            for (Iterator<String> it2 = nextLocalPropMap.keySet().iterator();it2.hasNext();){
                String nextKey = it2.next();
                //make sure all dir names have a / at the end
                if (nextKey.endsWith("Dir")){
                    String prop = (String)nextLocalPropMap.get(nextKey);
                    char lastChar = prop.charAt(prop.length()-1);
                    if (Character.isLetterOrDigit(lastChar)){
                        nextLocalPropMap.put(nextKey,prop+fileSeparator);
                    }
                }                
            }            
        }
    }
        
    
    
    /**
     * Save the props and their values in config file
     * @param configFile the config file
     */
    private void saveProps(File configFile)
    {
        try {
            PrintWriter out = new PrintWriter(new OutputStreamWriter(
                            new FileOutputStream(configFile),"UTF-8"), true);
            out.println("# GlobalProperties:");
            Set<String> globalPropSet = props.keySet();
            for (Iterator<String> it=globalPropSet.iterator();it.hasNext();){
                String key = it.next();
                if (isEditable(key)){
                    out.println(key+" "+props.get(key));      
                }
            }
            out.println();
            for (int i=0;i<compNames.length;i++){
                String key = compNames[i];
                SortedMap<String,String> nextProps = localProps.get(key);
                if (nextProps != null) {
                    out.println("# Properties for module "+key+":");
                    for (String localKey : nextProps.keySet()) {
                        out.println(localKey+" "+nextProps.get(localKey));                
                    }
                    out.println();
                }
            }
            out.close();
        } catch(Exception e) {
            throw new Error("Error writing config file", e);
        }
    
    
    }
    
    /**
     * Prompt the user for the basic props
     * (This is called if we don't have any props)
     * @param props the map of props to be filled
     */
    private boolean promptUserForBasicProps(SortedMap<String,String> basicprops){
        //fill in the map with the prop names and value templates
        String marybase = System.getProperty("MARYBASE");
        if ( marybase == null ) {
            marybase = "/path/to/marybase/";
        }
        basicprops.put(MARYBASE,marybase);
        basicprops.put(MARYBASEVERSION, Version.specificationVersion());
        String voicename = System.getProperty("VOICENAME");
        if ( voicename == null ) {
            voicename = "my_voice";
        }
        basicprops.put(VOICENAME, voicename);
        String gender = System.getProperty("GENDER");
        if ( gender == null ) {
            gender = "female";
        }
        basicprops.put(GENDER, gender);
        basicprops.put(DOMAIN,"general");
        String locale = System.getProperty("LOCALE");
        if ( locale == null ) {
            locale = "en_US";
        }
        basicprops.put(LOCALE, locale);
        basicprops.put(SAMPLINGRATE,"16000");
        String rootDir = new File(System.getProperty("user.dir")).getAbsolutePath()+fileSeparator;
        basicprops.put(ROOTDIR,rootDir.substring(0,rootDir.length()-1));
        basicprops.put(WAVDIR, rootDir+"wav"+fileSeparator);
        basicprops.put(LABDIR, rootDir+"lab"+fileSeparator);
        basicprops.put(LABEXT,".lab");        
        basicprops.put(TEXTDIR, rootDir+"text"+fileSeparator);
        basicprops.put(TEXTEXT,".txt");
        basicprops.put(PMDIR, rootDir+"pm"+fileSeparator);
        basicprops.put(PMEXT,".pm");
        basicprops.put(PTCDIR, rootDir+"ptc"+fileSeparator);
        basicprops.put(PTCEXT,".ptc");  
        basicprops.put(MARYSERVERHOST, "localhost");
        basicprops.put(MARYSERVERPORT, "59125");
        
        
        StringBuilder helpText = new StringBuilder();
        helpText.append("<html>\n<head>\n<title>SETTINGS HELP</title>\n"
                +"</head>\n<body>\n<dl>\n");
        for (Iterator<String> it=props2Help.keySet().iterator();it.hasNext();){
            String key = it.next();
            String value = (String) props2Help.get(key);
            helpText.append("<dt><strong>"+key+"</strong></dt>\n"
                    +"<dd>"+value+"</dd>\n");
        }
        
        helpText.append("</dl>\n</body>\n</html>");
        
        return displayProps(basicprops, helpText.toString(), "Please adjust the following settings:");
    }
    
    /**
     * Init the default props of the database layout
     * (the props that are not set during promptUserForBasicProps)
     * @param someProps the map of props to be filled
     * @return the map of default props
     */
    private SortedMap<String,String> initDefaultProps(SortedMap<String,String> someProps,boolean withBasicProps)
    {
        if (withBasicProps) {
            String marybase = System.getProperty("MARYBASE");
            if ( marybase == null ) {
                marybase = "/path/to/marybase/";
            }
            someProps.put(MARYBASE, marybase);
            String voicename = System.getProperty("VOICENAME");
            if ( voicename == null ) {
                voicename = "my_voice";
            }
            someProps.put(VOICENAME, voicename);
            String gender = System.getProperty("GENDER");
            if ( gender == null ) {
                gender = "female";
            }
            someProps.put(GENDER, gender);
            someProps.put(DOMAIN, "general");
            String locale = System.getProperty("LOCALE");
            if ( locale == null ) {
                locale = "en_US";
            }
            someProps.put(LOCALE, locale);
            someProps.put(SAMPLINGRATE, "16000");
            String rootDir = new File(System.getProperty("user.dir")).getAbsolutePath();
            someProps.put(ROOTDIR, rootDir.substring(0,rootDir.length()-1));
            someProps.put(WAVDIR, rootDir+"wav"+fileSeparator);
            someProps.put(LABDIR, rootDir+"lab"+fileSeparator);
            someProps.put(LABEXT,".lab");        
            someProps.put(TEXTDIR, rootDir+"text"+fileSeparator);
            someProps.put(TEXTEXT,".txt");
            someProps.put(PMDIR, rootDir+"pm"+fileSeparator);
            someProps.put(PMEXT,".pm");
            someProps.put(PTCDIR, rootDir+"ptc"+fileSeparator);
            someProps.put(PTCEXT,".ptc");
            someProps.put(MARYSERVERHOST, "localhost");
            someProps.put(MARYSERVERPORT, "59125");
            someProps.put(MARYBASEVERSION, Version.specificationVersion());
        }        
        String rootDir = getProp(ROOTDIR);
        char lastChar = rootDir.charAt(rootDir.length()-1);
        if (Character.isLetterOrDigit(lastChar)) {
            rootDir = rootDir+fileSeparator;
            someProps.put(ROOTDIR, rootDir);
        }
        
        someProps.put(CONFIGDIR, rootDir+"mary"+fileSeparator);
        someProps.put(FILEDIR, rootDir+"mary"+fileSeparator);
        someProps.put(MARYEXT, ".mry");
        someProps.put(BASENAMEFILE, rootDir+"basenames.lst");        
        someProps.put(TEMPDIR, rootDir+"temp"+fileSeparator);
        someProps.put(MARYXMLDIR, rootDir+"rawmaryxml"+fileSeparator);
        someProps.put(MARYXMLEXT, ".xml"); 
        someProps.put(PROMPTALLOPHONESDIR, rootDir+"prompt_allophones"+fileSeparator);
        someProps.put(ALLOPHONESDIR, rootDir+"allophones"+fileSeparator);
        // generate file location of allophone definition file from locale as:
        // MARYBASE/lib/modules/en/us/lexicon/allophones.en_US.xml
        // First normalise locale string (e.g., convert en-US to en_US)
        String locale = MaryUtils.string2locale(getProp(LOCALE)).toString();
        String[] localeParts = locale.split("_");
        String allophoneSetXml = getProp(MARYBASE)+"/lib/modules/"
            + localeParts[0].toLowerCase()
            + ((localeParts.length > 1) ? "/"+localeParts[1].toLowerCase() : "")
            + "/lexicon/allophones."+locale+".xml";
        someProps.put(ALLOPHONESET, allophoneSetXml);
        someProps.put(WAVEXT, ".wav");
        someProps.put(PHONELABDIR, rootDir+"phonelab"+fileSeparator);
        someProps.put(PHONEFEATUREDIR, rootDir+"phonefeatures"+fileSeparator);
        someProps.put(HALFPHONELABDIR, rootDir+"halfphonelab"+fileSeparator);
        someProps.put(HALFPHONEFEATUREDIR, rootDir+"halfphonefeatures"+fileSeparator);
        someProps.put(VOCALIZATIONSDIR, rootDir+"vocalizations"+fileSeparator);
        return someProps;
    }
    
    
    
    /**
     * Get the default props+values from the components
     * @return the default props of the components
     */
    private SortedMap<String,SortedMap<String,String>> getDefaultPropsFromComps()
    {
        SortedMap<String,SortedMap<String,String>> myLocalProps = new TreeMap<String, SortedMap<String,String>>();
        for (int i=0;i<components.length;i++){
            VoiceImportComponent nextComp = components[i];
            SortedMap<String,String> nextProps = nextComp.getDefaultProps(this);
            //get the name of the component
            String name = nextComp.getName();
            myLocalProps.put(name,nextProps);
        }
        return myLocalProps;
    }
    
    /**
     * Make sure that we have all files and dirs
     * that we will need
     */
    private void assureFileIntegrity()
    {
        /* check root dir */
        checkDir(ROOTDIR);
        /* check file dir */
        checkDir(FILEDIR);
        /* check config dir */
        checkDir(CONFIGDIR);
        /* check temp dir */
        checkDir(TEMPDIR);
        /* check maryxml dir */
        checkDir(MARYXMLDIR);
        
        checkDir(PROMPTALLOPHONESDIR);
        checkDir(ALLOPHONESDIR);
        
        /* check text dir */  
        //checkDir(TEXTDIR);
        checkDirinCurrentDir(TEXTDIR);
        /* check wav dir */
        File dir = new File(getProp(WAVDIR));
        //System.out.println(System.getProperty("user.dir")+System.getProperty("file.separator")+getProp(WAVDIR));
        if (!dir.exists()) {
            throw new Error("WAVDIR "+getProp(WAVDIR)+" does not exist!");
        }
        if (!dir.isDirectory()) {
            throw new Error("WAVDIR "+getProp(WAVDIR)+" is not a directory!");
        }
        /* check lab dir */
        //checkDir(LABDIR);
        checkDirinCurrentDir(LABDIR);
        //dir = new File(getProp(LABDIR));
        
    }
    
    /**
     * Test if a directory exists
     * and try to create it if not;
     * throws an error if the dir
     * can not be created
     * @param propname the prop containing the name of the dir
     */
    private void checkDir(String propname)
    {
        File dir = new File(getProp(propname));
        if (!dir.exists()) {
            System.out.print(propname+" "+getProp(propname)
                    +" does not exist; ");
            if (!dir.mkdir()){
                throw new Error("Could not create "+propname);
            }
            System.out.print("Created successfully.\n");
        }  
        if (!dir.isDirectory()) {
            throw new Error(propname+" "+getProp(propname)+" is not a directory!");
        }        
    }
    
    /**
     * Test if a directory exists
     * and try to create it if not;
     * throws an error if the dir
     * can not be created
     * @param propname the prop containing the name of the dir
     */
    private void checkDirinCurrentDir(String propname)
    {
        File dir = new File(getProp(propname));
        if (!dir.exists()) {
            System.out.print(propname+" "+getProp(propname)
                    +" does not exist; ");
            if (!dir.mkdir()) {
                throw new Error("Could not create "+propname);
            }
            System.out.print("Created successfully.\n");
        }  
        if (!dir.isDirectory()) {
            throw new Error(propname+" "+getProp(propname)+" is not a directory!");
        }        
    }
    
    /**
     * Load the basenamelist
     */
    private void loadBasenameList()
    {
        //test if basenamelist file exists
        File basenameFile = new File(getProp(BASENAMEFILE));
        if (!basenameFile.exists()) {
            //make basename list from wav files 
            System.out.println("Loading basename list from wav files");
            //bnl = new BasenameList(getProp(WAVDIR),getProp(WAVEXT));
            bnl = new BasenameList(getProp(WAVDIR),getProp(WAVEXT));
        } else {
            //load basename list from file
            try {
                System.out.println("Loading basename list from file "
                        +getProp(BASENAMEFILE));
                bnl = new BasenameList(getProp(BASENAMEFILE));
            } catch (IOException ioe) {
                throw new Error("Error loading basenames from file "
                        +getProp(BASENAMEFILE)+": "+ioe.getMessage());            
            }
        }
        System.out.println("Found "+bnl.getLength()+" files in basename list");
    }
    
    /**
     * Initialize the components
     */
    private void initializeComps()
    throws Exception
    {
        for (int i=0; i<components.length; i++) {
            SortedMap<String,String> nextProps = localProps.get(compNames[i]);
            components[i].initialise(bnl,nextProps);
        }
    }
    
    /**
     * Get the value of a property from the voice building DatabaseLayout, or from a VoiceImportComponent.
     * 
     * @param propertyName
     *            (e.g. "db.MARYBASE" or "VoicePackager.voiceType")
     * @return the property value
     * @throws NullPointerException
     *             if <b>propertyName</b> cannot be resolved
     */
    public String getProperty(String propertyName) {
        String[] propertyNameParts = propertyName.split("\\.");
        String component = propertyNameParts[0];
        String property = propertyNameParts[1];

        String value;
        if (component.equals("db")) {
            value = this.getProp(propertyName);
        } else {
            VoiceImportComponent voiceImportComponent = this.getComponent(component);
            value = voiceImportComponent.getProp(propertyName);
        }
        if (value == null) {
            throw new NullPointerException(propertyName + " cannot be resolved!");
        }
        return value;
    }
    
    public String getProp(String prop)
    {
        return props.get(prop);
    }
    
    public void setProp(String prop, String val)
    {
        props.put(prop,val);
    }
    public String getExternal(String prop)
    {
        if (external != null)
          return external.get(prop);
        else
          return null;
    }
    public boolean isEditable(String propname)
    {
        return !uneditableProps.contains(propname);
    }
    
    public boolean isInitialized()
    {
        return initialized;
    }
    
    /**
     * Get all props of all components
     * as an Array representation
     * for displaying with the SettingsGUI.
     * Does not include uneditable props.
     */
    public String[][] getAllPropsForDisplay()
    {
        List<String> keys = new ArrayList<String>();
        List<String> values = new ArrayList<String>();
        for (String key : props.keySet()) {
            keys.add(key);
            values.add(props.get(key));
        }
        for (int i=0;i<compNames.length;i++) {
            SortedMap<String,String> nextProps = localProps.get(compNames[i]);
            if (nextProps != null) { // some components don't have any properties
                for (String key : nextProps.keySet()) {
                    keys.add(key);
                    values.add(nextProps.get(key));
                }
            }
        }
        String[][] result = new String[keys.size()][];
        for (int i=0;i<result.length;i++) {
            String[] keyAndValue = new String[2];
            keyAndValue[0]=(String)keys.get(i);
            keyAndValue[1]=(String)values.get(i);
            result[i] = keyAndValue;
        }
        return result;
    }
    
    /**
     * Update the old props with the given props
     * @param newprops the new props 
     */
    public void updateProps(String[][] newprops)
    {
        for (int i=0;i<newprops.length;i++) {
            String[] keyAndValue = newprops[i];
            String key = keyAndValue[0];
            String value = keyAndValue[1];
            //find out if this is a global or a local prop
            if (key.startsWith("db.")) {
                //global prop
                if (isEditable(key))
                    setProp(key,value);
            } else {
                //local prop: get the name of the component
                String compName = key;
                try {
                    compName = key.substring(0,key.indexOf('.'));
                }
                catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Found malformed property key: " + key);
                }
                
                //update our representation of local props for this component
                if (localProps.containsKey(compName)) {
                    localProps.get(compName).put(key,value);
                } else {
                    SortedMap<String,String> keys2values = new TreeMap<String, String>();
                    keys2values.put(key,value);
                    localProps.put(compName,keys2values);
                    
                }
                //update the representation of props in the component
                compnames2comps.get(compName).setProp(key,value);
            }           
        }       
        //finally, save everything in config file
        if (initialized) {
            saveProps(new File(configFileName));
        }
    }
    
    public void initialiseComponent(VoiceImportComponent vic)
    throws Exception
    {
        String name = vic.getName();
        SortedMap<String,String> defaultProps = vic.getDefaultProps(this);
        if (!compnames2comps.containsKey(name)) {
            System.out.println("comp "+name+" not in db");
            vic.setupHelp();
            if (!displayProps(defaultProps,vic.getHelpText(),"The following properties are missing:"))
                return;
            saveProps(new File(configFileName));
        }
        vic.initialise(bnl, localProps.get(name));
     }
    
    public BasenameList getBasenames()
    {
        return bnl;
    }
    
    public String[] getCompNamesForDisplay()
    {
        String[] names = new String[compNames.length+1];
        names[0] = "Global properties";
        String[] sortedCompNames = new String[compNames.length];
        System.arraycopy(compNames,0,sortedCompNames,0,compNames.length);
        Arrays.sort(sortedCompNames);
        for (int i=1;i<names.length;i++) {
             names[i] = sortedCompNames[i-1];
        }
        return names;
    }
    
    private boolean displayProps(SortedMap/*<String,String-or-SortedMap<String,String>>*/ someProps, String helpText, String guiText){
        try {
            SettingsGUI gui = 
                new SettingsGUI(this, someProps,helpText,guiText);
            return gui.wasSaved();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Can not display props");
            return false;
        }
    }
    
    public Map<String,String> getComps2HelpText()
    {
        Map<String,String> comps2HelpText = new HashMap<String, String>();
        StringBuilder helpText = new StringBuilder();
        helpText.append("<html>\n<head>\n<title>SETTINGS HELP</title>\n"
                +"</head>\n<body>\n<dl>\n");
        
        for (Iterator<String> it=props2Help.keySet().iterator();it.hasNext();) {
            String key = it.next();
            String value = (String) props2Help.get(key);
            helpText.append("<dt><strong>"+key+"</strong></dt>\n"
                    +"<dd>"+value+"</dd>\n");
        }
        
        helpText.append("</dl>\n</body>\n</html>");
        comps2HelpText.put("Global properties",helpText.toString());
        for (int i=0;i<components.length;i++) {
            comps2HelpText.put(compNames[i],components[i].getHelpText());
        }
        return comps2HelpText;
    }
        
}



