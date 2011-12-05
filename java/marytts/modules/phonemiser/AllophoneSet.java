/**
 * Copyright 2000-2006 DFKI GmbH.
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
package marytts.modules.phonemiser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import marytts.datatypes.MaryXML;
import marytts.exceptions.MaryConfigurationException;
import marytts.exceptions.NoSuchPropertyException;
import marytts.modules.synthesis.Voice;
import marytts.server.MaryProperties;
import marytts.util.MaryUtils;
import marytts.util.dom.DomUtils;
import marytts.util.dom.MaryDomUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.traversal.NodeIterator;
import org.xml.sax.SAXException;


public class AllophoneSet
{
    private static Map<String, AllophoneSet> allophoneSets = new HashMap<String, AllophoneSet>();

    /** Return the allophone set specified by the given filename.
     * It will only be loaded if it was not loaded before.
     * @param filename
     * @return the allophone set, if one can be created. This method will never return null.
     * @throws MaryConfigurationException if no allophone set can be loaded from the given file.
     */
    public static AllophoneSet getAllophoneSet(String filename)
    throws MaryConfigurationException
    {
        AllophoneSet as = allophoneSets.get(filename);
        if (as == null) {
            // Need to load it:
            as = new AllophoneSet(filename);
            allophoneSets.put(filename, as);
        }
        assert as != null;
        return as;
    }

    /**
     * For an element in a MaryXML document, do what you can to determine
     * the appropriate AllophoneSet. First search for the suitable voice,
     * then if that fails, go by locale.
     * @param e
     * @return an allophone set if there is any way of determining it, or null.
     * @throws MaryConfigurationException if a suitable allophone set exists in principle, but there were problems loading it.
     */
    public static AllophoneSet determineAllophoneSet(Element e) 
    throws MaryConfigurationException {
        AllophoneSet allophoneSet = null;
        Element voice = (Element) MaryDomUtils.getAncestor(e, MaryXML.VOICE);
        Voice maryVoice = Voice.getVoice(voice);
        if (maryVoice == null) {
            // Determine Locale in order to use default voice
            Locale locale = MaryUtils.string2locale(e.getOwnerDocument().getDocumentElement().getAttribute("xml:lang"));
            maryVoice = Voice.getDefaultVoice(locale);
        }
        if (maryVoice != null) {
            allophoneSet = maryVoice.getAllophoneSet();
        } else {
            Locale locale = MaryUtils.string2locale(e.getOwnerDocument().getDocumentElement().getAttribute("xml:lang"));
            allophoneSet = determineAllophoneSet(locale);
        }
        return allophoneSet;
    }

    /**
     * Try to determine the Allophone set to use for the given locale.
     * @param allophoneSet
     * @param locale
     * @return the allophone set defined for the given locale, or null if no such allophone set can be determined.
     * @throws MaryConfigurationException if an allophone set exists for the given locale in principle, but there were problems loading it.
     */
    public static AllophoneSet determineAllophoneSet(Locale locale)
    throws MaryConfigurationException {
        AllophoneSet allophoneSet = null;
        String propertyPrefix = MaryProperties.localePrefix(locale);
        if (propertyPrefix != null) {
            String propertyName = propertyPrefix + ".allophoneset";
            String filename = MaryProperties.needFilename(propertyName);
            allophoneSet = AllophoneSet.getAllophoneSet(filename);
        }
        return allophoneSet;
    }


    ////////////////////////////////////////////////////////////////////

    private String name; // the name of the allophone set
    private Locale locale; // the locale of the allophone set, e.g. US English
    // The map of segment objects, indexed by their phonetic symbol:
    private Map<String, Allophone> allophones = null;
    // Map feature names to the list of possible values in this AllophoneSet
    private Map<String, String[]> featureValueMap = null;
    
    private Allophone silence = null;
    // The number of characters in the longest Allophone symbol
    private int maxAllophoneSymbolLength = 1;

    private AllophoneSet(String filename)
    throws MaryConfigurationException
    {
        allophones = new TreeMap<String, Allophone>();
        // parse the xml file:
        Document document;
        try {
            document = DomUtils.parseDocument(new File(filename));
        } catch (Exception e) {
            throw new MaryConfigurationException("Cannot parse allophone file '"+filename+"'", e);
        }
        Element root = document.getDocumentElement();
        name = root.getAttribute("name");
        String xmlLang = root.getAttribute("xml:lang");
        locale = MaryUtils.string2locale(xmlLang);
        String[] featureNames = root.getAttribute("features").split(" ");
        NodeIterator ni = MaryDomUtils.createNodeIterator(document, root, "vowel", "consonant", "silence", "tone");
        Element a;
        while ((a = (Element) ni.nextNode()) != null) {
            Allophone ap = new Allophone(a, featureNames);
            if (allophones.containsKey(ap.name()))
                throw new MaryConfigurationException("File "+filename+" contains duplicate definition of allophone '"+ap.name()+"'!");
            allophones.put(ap.name(), ap);
            if (ap.isPause()) {
                if (silence != null)
                    throw new MaryConfigurationException("File "+filename+" contains more than one silence symbol: '"+silence.name()+"' and '"+ap.name()+"'!");
                silence = ap;
            }
            int len = ap.name().length();
            if (len > maxAllophoneSymbolLength) {
                maxAllophoneSymbolLength = len;
            }
        }
        if (silence == null)
            throw new MaryConfigurationException("File "+filename+" does not contain a silence symbol");
        // Fill the list of possible values for all features
        // such that "0" comes first and all other values are sorted alphabetically
        featureValueMap = new TreeMap<String, String[]>();
        for (String feature : featureNames) {
            Set<String> featureValueSet = new TreeSet<String>();
            for (Allophone ap : allophones.values()) {
                featureValueSet.add(ap.getFeature(feature));
            }
            if (featureValueSet.contains("0")) featureValueSet.remove("0");
            String[] featureValues = new String[featureValueSet.size()+1];
            featureValues[0] = "0";
            int i=1;
            for (String f : featureValueSet) {
                featureValues[i++] = f;
            }
            featureValueMap.put(feature, featureValues);
        }
        // Special "vc" feature:
        featureValueMap.put("vc", new String[] {"0", "+", "-"});
    }

    public Locale getLocale()
    {
        return locale;
    }
    
    public Syllabifier getSyllabifier()
    {
        return new Syllabifier(this);
    }


    /**
     * Get the Allophone with the given name
     * 
     * @param ph
     *            name of Allophone to get
     * @return the Allophone, or null if there is no such Allophone.
     */
    public Allophone getAllophone(String ph) {
        if (ph == null) {
            return null;
        }
        return allophones.get(ph);
    }

    /**
     * Obtain the silence allophone in this AllophoneSet
     * @return
     */
    public Allophone getSilence()
    {
        return silence;
    }
    
    /**
     * For the Allophone with name ph, return the value of the named feature.
     * @param ph
     * @param featureName
     * @return the allophone feature, or null if either the allophone or the feature does not exist.
     */
    public String getPhoneFeature(String ph, String featureName)
    {
        if (ph == null) return null;
        Allophone a = allophones.get(ph);
        if (a == null) return null;
        return a.getFeature(featureName);
    }
    
    /**
     * Get the list of available phone features for this allophone set.
     * @return
     */
    public Set<String> getPhoneFeatures()
    {
        return Collections.unmodifiableSet(featureValueMap.keySet());
    }
    
    /**
     * For the given feature name, get the list of all possible values
     * that the feature can take in this allophone set.
     * @param featureName
     * @throws IllegalArgumentException if featureName is not a known feature name.
     * @return the list of values, "0" first.
     */
    public String[] getPossibleFeatureValues(String featureName)
    {
        String[] vals = featureValueMap.get(featureName);
        if (vals == null) throw new IllegalArgumentException("No such feature: "+featureName);
        return vals;
    }
    
    /**
     * This returns the names of all allophones contained in this AllophoneSet,
     * as a Set of Strings
     */
    public Set<String> getAllophoneNames()
    {
        Iterator<String> it = allophones.keySet().iterator();
        Set<String> allophoneKeySet = new TreeSet<String>();
        while(it.hasNext()){
            String keyString = it.next();
            if(!allophones.get(keyString).isTone()){
                allophoneKeySet.add(keyString);
            }
        }
        return allophoneKeySet;
    }

    /**
     * Split a phonetic string into allophone symbols. Symbols representing
     * primary and secondary stress, syllable boundaries, and spaces, will be silently skipped.
     * @param allophoneString the phonetic string to split
     * @return an array of Allophone objects corresponding to the string given as input
     * @throws IllegalArgumentException if the allophoneString contains unknown symbols.
     */
    public Allophone[] splitIntoAllophones(String allophoneString)
    {
        List<String> phones = splitIntoAllophoneList(allophoneString, false);
        Allophone[] allos = new Allophone[phones.size()];
        for (int i=0; i<phones.size(); i++) {
            allos[i] = getAllophone(phones.get(i));
            assert allos[i] != null : "Symbol '"+phones.get(i)+"' really should be an allophone, but isn't!";
        }
        return allos;
    }
    
    
    /**
     * Split allophone string into a list of allophone symbols.
     * Include stress markers (',) and syllable boundaries (-), skip space characters.
     * @param allophoneString
     * @throws IllegalArgumentException if the string contains illegal symbols.
     * @return a String containing allophones and stress markers / syllable boundaries, separated with spaces
     */
    public String splitAllophoneString(String allophoneString)
    {
        List<String> phones = splitIntoAllophoneList(allophoneString, true);
        StringBuilder pronunciation = new StringBuilder();
        for(String a : phones) {
            if (pronunciation.length()>0) pronunciation.append(" ");
            pronunciation.append(a);
        }
        return pronunciation.toString();
    }

    /**
     * Split allophone string into a list of allophone symbols.
     * Include (or ignore, depending on parameter 'includeStressAndSyllableMarkers')
     *  stress markers (',), syllable boundaries (-). Ignores space characters.
     * @param allophoneString
     * @param includeStressAndSyllableMarkers whether to skip stress markers and syllable
     * boundaries. If true, will return each such marker as a separate string in the list.
     * @throws IllegalArgumentException if the string contains illegal symbols.
     * @return a list of allophone objects.
     */
    private List<String> splitIntoAllophoneList(String allophoneString, boolean includeStressAndSyllableMarkers)
    {
        List<String> phones = new ArrayList<String>();
        boolean haveSeenNucleus = false;
        for (int i=0; i<allophoneString.length(); i++) {
            String one = allophoneString.substring(i,i+1);
            
            if ("',-".contains(one)) {
                if (includeStressAndSyllableMarkers) phones.add(one); 
                continue;
            } else if (one.equals(" ")) {
                continue;
            }
            // Try to cut off individual segments, 
            // starting with the longest prefixes:
            Allophone ph = null;
            for (int l=maxAllophoneSymbolLength; l>=1; l--) {
                if (i+l <= allophoneString.length()) {
                    String s = allophoneString.substring(i, i+l);
                    // look up in allophone map:
                    ph = getAllophone(s);
                    if (ph != null) {
                        // OK, found a symbol of length l.
                        i += l-1; // together with the i++ in the for loop, move by l
                        break;
                    }
                }
            }
            if (ph != null) {
                // have found a valid phone
                phones.add(ph.name());
            } else {
                throw new IllegalArgumentException("Found unknown symbol `" + 
                        allophoneString.charAt(i) +
                        "' in phonetic string `" + allophoneString + "' -- ignoring.");
            }
        }
        return phones;
    }
    
    /**
     * Check whether the given allophone string has a correct syntax 
     * according to this allophone set.
     * @param allophoneString
     * @return true if the syntax is correct, false otherwise.
     */
    public boolean checkAllophoneSyntax(String allophoneString)
    {
        try {
            splitIntoAllophoneList(allophoneString, false);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    
}

