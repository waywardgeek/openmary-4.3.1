/**
 * Copyright 2010 DFKI GmbH.
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
package marytts.modules.acoustic;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import marytts.cart.io.DirectedGraphReader;
import marytts.datatypes.MaryXML;
import marytts.exceptions.MaryConfigurationException;
import marytts.exceptions.SynthesisException;
import marytts.features.FeatureDefinition;
import marytts.features.FeatureProcessorManager;
import marytts.features.FeatureRegistry;
import marytts.features.TargetFeatureComputer;
import marytts.unitselection.select.Target;
import marytts.util.MaryUtils;
import marytts.util.dom.DomUtils;
import marytts.util.dom.MaryDomUtils;
import marytts.util.math.MathUtils;
import marytts.util.math.Polynomial;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.NodeIterator;
import org.w3c.dom.traversal.TreeWalker;

/**
 * This module will apply prosody modifications to the already predicted values (dur and f0) in the acoustparams
 * This class also support SSML recommendations of 'prosody' element
 * @author Sathish Pammi
 * 
 */
public class ProsodyElementHandler {
    
    private int F0CONTOUR_LENGTH = 101; // Assumption: the length of f0 contour of a prosody element is 101 (0,1,2....100)
                                        // DONOT change this number as some index numbers are based on this number    
    private DecimalFormat df; 
    private Logger logger = MaryUtils.getLogger("ProsodyElementHandler");
    
    public ProsodyElementHandler(){
        df = new DecimalFormat("#0.0");
    }
    
    /**
     * A method to modify prosody modifications
     * @param doc - MARY XML Document
     * 
     */
    public void process(Document doc) {
        
        TreeWalker tw = MaryDomUtils.createTreeWalker(doc, MaryXML.PROSODY);
        Element e = null;
        
        // read prosody tags recursively 
        while ((e = (Element) tw.nextNode()) != null) {
            logger.debug("Found prosody element around '"+DomUtils.getPlainTextBelow(e)+"'");
            boolean hasRateAttribute    = e.hasAttribute("rate");
            boolean hasContourAttribute = e.hasAttribute("contour");
            boolean hasPitchAttribute   = e.hasAttribute("pitch");
            
            NodeList nl = e.getElementsByTagName("ph");
            // guard against degenerate phrases without any <ph> elements:
            if (nl.getLength() == 0) {
                continue;
            }
            
            // if prosody element contains 'rate' attribute, apply rate specifications
            if ( hasRateAttribute ) {
                applySpeechRateSpecifications(nl, e.getAttribute("rate"));
            }
            
            // if prosody element contains any 'pitch' modifications
            if ( hasPitchAttribute ||  hasContourAttribute ) {
                
                double[] f0Contour  = getF0Contour(nl);
                double[] coeffs     = Polynomial.fitPolynomial(f0Contour, 1);
                double[] baseF0Contour = Polynomial.generatePolynomialValues(coeffs, F0CONTOUR_LENGTH, 0, 1);
                double[] diffF0Contour = new double[F0CONTOUR_LENGTH];
                
                // Extract base contour from original contour
                for ( int i=0; i < f0Contour.length ; i++ ) {
                    diffF0Contour[i] =  f0Contour[i] - baseF0Contour[i];
                }
                
                // Set pitch modifications to base contour (first order polynomial fit contour) 
                if ( hasPitchAttribute ) {
                    baseF0Contour = applyPitchSpecifications( nl, baseF0Contour, e.getAttribute("pitch") );
                }
                
                // Set contour modifications to base contour (first order polynomial fit contour)
                if( hasContourAttribute ) {
                    baseF0Contour = applyContourSpecifications( nl, baseF0Contour, e.getAttribute("contour") );
                }
                
                // Now, imposing back the diff. contour
                for ( int i=0; i < f0Contour.length ; i++ ) {
                    f0Contour[i] =  diffF0Contour[i] + baseF0Contour[i];
                }
                
                setModifiedContour(nl, f0Contour);
            }
            
        }      
   }
    
    /**
     * To apply 'rate' specifications to NodeList (with only 'ph' elements)
     * @param nl - NodeList of 'ph' elements
     * @param rateAttribute - Speech rate attribute
     */
    private void applySpeechRateSpecifications(NodeList nl, String rateAttribute) {
      
        if ( "".equals(rateAttribute) ) { 
            return;
        }
        
        // if the format contains a fixed label 
        boolean hasLabel = rateAttribute.equals("x-slow") || rateAttribute.equals("slow") ||  rateAttribute.equals("medium") 
                                                   || rateAttribute.equals("fast") || rateAttribute.equals("x-fast") || rateAttribute.equals("default");
        if ( hasLabel ) { 
            rateAttribute = rateLabels2RelativeValues(rateAttribute);
        }
        
        // if the value is non-negative percentage (as described in W3C SSML)
        if ( !(rateAttribute.startsWith("+") || rateAttribute.startsWith("-")) && rateAttribute.endsWith("%")) {
            double absolutePercentage = new Double(rateAttribute.substring(0, rateAttribute.length()-1)).doubleValue();
            if ( absolutePercentage == 100 ) { // no change
                return;
            }
            else {
                rateAttribute = df.format(absolutePercentage / 100);
            }
        }
        
        // if the format contains a positive decimal number
        boolean hasPositiveInteger = !rateAttribute.endsWith("%") && (!rateAttribute.startsWith("+") || !rateAttribute.startsWith("-"));
        if ( hasPositiveInteger ) { 
            rateAttribute = positiveInteger2RelativeValues(rateAttribute);
        }
        
        //Pattern p = Pattern.compile("[+|-]\\d+%");
        Pattern p = Pattern.compile("[+|-][0-9]+(.[0-9]+)?[%]?");
        // Split input with the pattern
        Matcher m = p.matcher(rateAttribute);
        if ( m.find() ) {
            double percentage = new Double(rateAttribute.substring(1, rateAttribute.length()-1)).doubleValue();
            if ( rateAttribute.startsWith("+") ) {
                modifySpeechRate(nl, percentage, true);
            }
            else {
                modifySpeechRate(nl, percentage, false);
            }
        }
    }
    
    /**
     * apply given pitch specifications to the base contour 
     * @param nl
     * @param baseF0Contour
     * @param pitchAttribute
     * @return
     */
    private double[] applyPitchSpecifications(NodeList nl, double[] baseF0Contour, String pitchAttribute) {
        
        if ( "".equals(pitchAttribute) ) { 
            return baseF0Contour;
        }
        
        boolean hasLabel = pitchAttribute.equals("x-low") || pitchAttribute.equals("low") ||  pitchAttribute.equals("medium") 
                                                   || pitchAttribute.equals("high") || pitchAttribute.equals("x-high") || pitchAttribute.equals("default");
        
        if ( hasLabel ) { 
            pitchAttribute = pitchLabels2RelativeValues(pitchAttribute);
        }
        
        boolean hasFixedValue = pitchAttribute.endsWith("Hz") && !(pitchAttribute.startsWith("+") || pitchAttribute.startsWith("-"));
        
        if ( hasFixedValue ) {
            pitchAttribute = fixedValue2RelativeValue(pitchAttribute, baseF0Contour);
        }
        
        if ( pitchAttribute.startsWith("+") ) {
            if ( pitchAttribute.endsWith("%") ) {  // type example: +20%
                double modificationPitch = (new Float(pitchAttribute.substring(1, pitchAttribute.length()-1))).doubleValue();
                for ( int i=0; i < baseF0Contour.length; i++ ) {
                    baseF0Contour[i] = baseF0Contour[i] + (baseF0Contour[i] * (modificationPitch / 100.0));
                }
            }
            else if ( pitchAttribute.endsWith("Hz") ) { // type example: +55Hz
                double modificationPitch = (new Float(pitchAttribute.substring(1, pitchAttribute.length()-2))).doubleValue();
                for ( int i=0; i < baseF0Contour.length; i++ ) {
                    baseF0Contour[i] = baseF0Contour[i] + modificationPitch;
                }
            }
            else if ( pitchAttribute.endsWith("st") ) { // type example: +12st
                double modificationPitch = (new Float(pitchAttribute.substring(1, pitchAttribute.length()-2))).doubleValue();
                for ( int i=0; i < baseF0Contour.length; i++ ) {
                    baseF0Contour[i] = Math.exp(modificationPitch * Math.log(2) / 12) * baseF0Contour[i];
                }
            }
        }
        else if ( pitchAttribute.startsWith("-") ) {
            if ( pitchAttribute.endsWith("%") ) {   // type example: -20%
                double modificationPitch = (new Float(pitchAttribute.substring(1, pitchAttribute.length()-1))).doubleValue();
                for ( int i=0; i < baseF0Contour.length; i++ ) {
                    baseF0Contour[i] = baseF0Contour[i] - (baseF0Contour[i] * (modificationPitch / 100.0));
                }
            }
            else if ( pitchAttribute.endsWith("Hz") ) { // type example: -88Hz
                double modificationPitch = (new Float(pitchAttribute.substring(1, pitchAttribute.length()-2))).doubleValue();
                for ( int i=0; i < baseF0Contour.length; i++ ) {
                    baseF0Contour[i] = baseF0Contour[i] - modificationPitch;
                }
            }
            else if ( pitchAttribute.endsWith("st") ) { // type example: -3st
                double modificationPitch = (new Float(pitchAttribute.substring(1, pitchAttribute.length()-2))).doubleValue();
                for ( int i=0; i < baseF0Contour.length; i++ ) {
                    baseF0Contour[i] = Math.exp(-1 * modificationPitch * Math.log(2) / 12) * baseF0Contour[i];
                }
            }
        }
        
      return baseF0Contour;       
    }
    

    /**
     * Apply given contour specifications to base f0 contour 
     * @param nl
     * @param prosodyElement
     */
    private double[] applyContourSpecifications(NodeList nl, double[] baseF0Contour, String contourAttribute) {
        
        if ( "".equals(contourAttribute) ) { 
            return baseF0Contour;
        }
        
        Map<String, String> f0Specifications = getContourSpecifications(contourAttribute);
        Iterator<String> it =  f0Specifications.keySet().iterator();
        double[] modifiedF0Values = new double[F0CONTOUR_LENGTH];
        Arrays.fill(modifiedF0Values, 0.0);
        
        if ( baseF0Contour.length != modifiedF0Values.length ) {
            throw new RuntimeException("The lengths of two arrays are not same!");
        }
        
        modifiedF0Values[0] = baseF0Contour[0];
        modifiedF0Values[ modifiedF0Values.length - 1 ] = baseF0Contour[ modifiedF0Values.length - 1 ];
        
        while ( it.hasNext() ) {
        
            String percent = it.next();
            String f0Value = f0Specifications.get( percent );
            
            boolean hasLabel = f0Value.equals("x-low") || f0Value.equals("low") ||  f0Value.equals("medium") 
                                || f0Value.equals("high") || f0Value.equals("x-high") || f0Value.equals("default");

            if ( hasLabel ) {
                f0Value = pitchLabels2RelativeValues(f0Value);
            }
            
            // if not preceded by '+' or '-', add '+' at the beginning (and also not ends with 'Hz')
            if ( (!f0Value.startsWith("+") && !f0Value.startsWith("-")) && (!f0Value.endsWith("Hz")) ) {
                f0Value = "+" + f0Value;
            }
            
            int percentDuration  =  Math.round((new Float(percent.substring(0, percent.length()-1))).floatValue());
            if ( percentDuration > 100 ) {
                throw new RuntimeException("Given percentage of duration ( "+ percentDuration+"%"+ " ) is illegal.. ") ;
            }
            
            //System.out.println( percent  + " " + f0Value );
            
            if ( f0Value.startsWith("+") ) {
                if ( f0Value.endsWith("%") ) {
                    double f0Mod = (new Double(f0Value.substring( 1, f0Value.length()-1 ))).doubleValue();
                    modifiedF0Values[percentDuration] = baseF0Contour[percentDuration] + (baseF0Contour[percentDuration] * (f0Mod / 100.0));
                }
                else if ( f0Value.endsWith("Hz") ) {
                    float f0Mod = (new Float(f0Value.substring( 1, f0Value.length()-2 ))).floatValue();
                    modifiedF0Values[percentDuration] = baseF0Contour[percentDuration] + f0Mod ;
                }
                else if ( f0Value.endsWith("st") ) {
                    float semiTone = (new Float(f0Value.substring( 1, f0Value.length()-2 ))).floatValue();
                    modifiedF0Values[percentDuration] = Math.exp(semiTone * Math.log(2) / 12) * baseF0Contour[percentDuration];
                }
            }
            else if ( f0Value.startsWith("-") ) {
                if ( f0Value.endsWith("%") ) {
                    double f0Mod = (new Double(f0Value.substring( 1, f0Value.length()-1 ))).doubleValue();
                    modifiedF0Values[percentDuration] = baseF0Contour[percentDuration] - (baseF0Contour[percentDuration] * (f0Mod / 100.0));
                }
                else if ( f0Value.endsWith("Hz") ) {
                    float f0Mod = (new Float(f0Value.substring( 1, f0Value.length()-2 ))).floatValue();
                    modifiedF0Values[percentDuration] = baseF0Contour[percentDuration] - f0Mod ;
                }
                else if ( f0Value.endsWith("st") ) {
                    float semiTone = (new Float(f0Value.substring( 1, f0Value.length()-2 ))).floatValue();
                    modifiedF0Values[percentDuration] = Math.exp(-1 * semiTone * Math.log(2) / 12) * baseF0Contour[percentDuration];
                }
            }
            else {
                if ( f0Value.endsWith("Hz") ) {
                    float f0Mod = (new Float(f0Value.substring( 0, f0Value.length()-2 ))).floatValue();
                    modifiedF0Values[percentDuration] = f0Mod;
                }
            }
        }
        
      return MathUtils.interpolateNonZeroValues(modifiedF0Values);
    }


    /**
     * To set duration specifications according to 'rate' requirements
     * @param nl - NodeList of 'ph' elements; All elements in this NodeList should be 'ph' elements only
     *             All these 'ph' elements should contain 'd', 'end' attributes 
     * @param percentage the percentage of increment or decrement in speech rate
     * @param increaseSpeechRate whether the request is to increase (value true) or decrease (value false) to speech rate     *  
     */
    private void modifySpeechRate(NodeList nl, double percentage, boolean increaseSpeechRate) {
        
        assert nl != null;
        
        for ( int i=0; i < nl.getLength(); i++ ) {
            Element e = (Element) nl.item(i); 
            assert "ph".equals(e.getNodeName()) : "NodeList should contain 'ph' elements only";
            if ( !e.hasAttribute("d") ) {
                continue;
            }
            
            double durAttribute = new Double(e.getAttribute("d")).doubleValue();
            double newDurAttribute;
            
            if ( increaseSpeechRate ) {
                newDurAttribute = durAttribute - (percentage * durAttribute / 100);
            } else {
                newDurAttribute = durAttribute + (percentage * durAttribute / 100);
            }
            
            e.setAttribute("d", newDurAttribute+"");
            //System.out.println(durAttribute+" = " +newDurAttribute);
        }
        
        Element e = (Element) nl.item(0);
        
        Element rootElement = e.getOwnerDocument().getDocumentElement();
        NodeIterator nit = MaryDomUtils.createNodeIterator(rootElement, MaryXML.PHONE, MaryXML.BOUNDARY);
        Element nd; 
        double duration = 0.0;
        for ( int i=0; (nd = (Element) nit.nextNode()) != null; i++ ) {
            if ( "boundary".equals(nd.getNodeName()) ) {
                if ( nd.hasAttribute("duration") ) {
                    duration += new Double(nd.getAttribute("duration")).doubleValue();
                }
            }
            else {
                if ( nd.hasAttribute("d") ) {
                    duration += new Double(nd.getAttribute("d")).doubleValue();
                }
            }
            double endTime = 0.001 * duration;
            if (!nd.getNodeName().equals(MaryXML.BOUNDARY)) {
                // setting "end" attr for boundaries does not have the intended effect, since elsewhere, only the "duration" attr
                // is used for boundaries
                nd.setAttribute("end", String.valueOf(endTime));
            }
            //System.out.println(nd.getNodeName()+" = " +nd.getAttribute("end"));
        }
      
    }

    // we need a public getter with variable array size to call from unitselection.analysis
    private double[] getF0Contour(NodeList nl) {
        return getF0Contour(nl, F0CONTOUR_LENGTH); // Assume contour has F0CONTOUR_LENGTH frames
    }
    
    /**
     * To get a continuous pitch contour from nodelist of "ph" elements
     * @param nl - NodeList of 'ph' elements; All elements in this NodeList should be 'ph' elements only
     *             All these 'ph' elements should contain 'd', 'end' attributes  
     * @param arraysize the length of the output pitch contour array (arraysize > 0)
     * @return a double array of pitch contour
     * @throws IllegalArgumentException if NodeList is null or it contains elements other than 'ph' elements
     * @throws IllegalArgumentException if given 'ph' elements do not contain 'd' or 'end' attributes
     * @throws IllegalArgumentException if given arraysize is not greater than zero
     */
    public double[] getF0Contour(NodeList nl, int arraysize) {
        
        if ( nl == null || nl.getLength() == 0 ) {
            throw new IllegalArgumentException("Input NodeList should not be null or zero length list"); 
        }
        if ( arraysize <= 0 ) {
            throw new IllegalArgumentException("Given arraysize should be is greater than zero"); 
        }
        
        // A sanity checker for NodeList: for 'ph' elements only condition
        for ( int i=0; i < nl.getLength(); i++ ) {
            Element e = (Element) nl.item(i);
            if ( !"ph".equals(e.getNodeName()) ) {
                throw new IllegalArgumentException("Input NodeList should contain 'ph' elements only");
            }
            if ( !e.hasAttribute("d") || !e.hasAttribute("end") ) {
                throw new IllegalArgumentException("All 'ph' elements should contain 'd' and 'end' attributes");
            }
        }
        
        Element firstElement =  (Element) nl.item(0);
        Element lastElement =  (Element) nl.item(nl.getLength()-1);
        
        double[] contour = new double[arraysize];
        Arrays.fill(contour, 0.0);
        
        double fEnd = (new Double(firstElement.getAttribute("end"))).doubleValue();
        double fDuration = 0.001 * (new Double(firstElement.getAttribute("d"))).doubleValue();
        double lEnd = (new Double(lastElement.getAttribute("end"))).doubleValue();
        double fStart = fEnd - fDuration; // 'prosody' tag starting point
        double duration = lEnd - fStart;  // duaration of 'prosody' modification request
        
        Map<Integer, Integer> f0Map;
        
        for ( int i=0; i < nl.getLength(); i++ ) {
            Element e = (Element) nl.item(i);
            String f0Attribute = e.getAttribute("f0");
            
            if( f0Attribute == null || "".equals(f0Attribute) ) {
                continue;
            }
            
            double phoneEndTime  = (new Double(e.getAttribute("end"))).doubleValue();
            double phoneDuration = 0.001 * (new Double(e.getAttribute("d"))).doubleValue();
            //double localStartTime = endTime - phoneDuration;
            
            f0Map = getPhoneF0Data(e.getAttribute("f0"));
            
            Iterator<Integer> it =  f0Map.keySet().iterator();
            while(it.hasNext()){
                Integer percent = it.next();
                Integer f0Value = f0Map.get(percent);
                double partPhone = phoneDuration * (percent.doubleValue()/100.0);
                int placeIndex  = (int) Math.floor((( ((phoneEndTime - phoneDuration) - fStart ) +  partPhone ) * arraysize ) / (double) duration );
                if ( placeIndex >= arraysize ) {
                    placeIndex = arraysize - 1;
                } else if ( placeIndex < 0) {
                    placeIndex = 0;
                }
                contour[placeIndex] = f0Value.doubleValue();
            }
        }
        
        return MathUtils.interpolateNonZeroValues(contour);
    }
    
    
    /**
     * To set new modified contour into XML
     * @param nl
     * @param contour
     */
    private void setModifiedContour(NodeList nl, double[] contour) {
        
        Element firstElement =  (Element) nl.item(0);
        Element lastElement =  (Element) nl.item(nl.getLength()-1);
        
        double fEnd = (new Double(firstElement.getAttribute("end"))).doubleValue();
        double fDuration = 0.001 * (new Double(firstElement.getAttribute("d"))).doubleValue();
        double lEnd = (new Double(lastElement.getAttribute("end"))).doubleValue();
        double fStart = fEnd - fDuration; // 'prosody' tag starting point
        double duration = lEnd - fStart;  // duaration of 'prosody' modification request
        
        Map<Integer, Integer> f0Map;
        
        for ( int i=0; i < nl.getLength(); i++ ) {
            
            Element e = (Element) nl.item(i);
            String f0Attribute = e.getAttribute("f0");
            
            if( f0Attribute == null || "".equals(f0Attribute) ) {
                continue;
            }
            
            double phoneEndTime       = (new Double(e.getAttribute("end"))).doubleValue();
            double phoneDuration = 0.001 * (new Double(e.getAttribute("d"))).doubleValue();
            
            Pattern p = Pattern.compile("(\\d+,\\d+)");
            
            // Split input with the pattern
            Matcher m = p.matcher(e.getAttribute("f0"));
            String setF0String = "";
            while ( m.find() ) {
                String[] f0Values = (m.group().trim()).split(",");
                Integer percent = new Integer(f0Values[0]);
                Integer f0Value = new Integer(f0Values[1]);
                double partPhone = phoneDuration * (percent.doubleValue()/100.0);
                
                int placeIndex  = (int) Math.floor((( ((phoneEndTime - phoneDuration) - fStart ) +  partPhone ) * F0CONTOUR_LENGTH ) / (double) duration );
                if ( placeIndex >= F0CONTOUR_LENGTH ) { 
                    placeIndex = F0CONTOUR_LENGTH - 1;
                }
                setF0String = setF0String + "(" + percent + "," +(int) contour[placeIndex] + ")" ;          
                
            }
          
            e.setAttribute("f0", setF0String);
        }    
   }


    /**
     * To get prosody contour specifications by parsing 'contour' attribute values
     * @param attribute - 'contour' attribute, it should not be null
     *        Expected format: '(0%, +10%)(50%,+30%)(95%,-10%)'  
     * @return HashMap that contains prosody contour specifications
     *         it returns empty map if given attribute is not in expected format
     */
    private Map<String, String> getContourSpecifications(String attribute) {
        
        assert attribute != null;
        assert !"".equals(attribute) : "given attribute should not be empty string";
                
        Map<String, String> f0Map = new HashMap<String, String>();
        Pattern p = Pattern.compile("\\(\\s*[0-9]+(.[0-9]+)?[%]\\s*,\\s*(x-low|low|medium|high|x-high|default|[+|-]?[0-9]+(.[0-9]+)?(%|Hz|st)?)\\s*\\)");
        
        // Split input with the pattern
        Matcher m = p.matcher(attribute);
        while ( m.find() ) {
            //System.out.println(m.group());
            String singlePair = m.group().trim();
            String[] f0Values = singlePair.substring(1,singlePair.length()-1).split(",");
            f0Map.put(f0Values[0].trim(), f0Values[1].trim());
        }
        return f0Map;
    }


    /**
     * To parse 'f0' attribute and to get f0 specifications
     * @param attribute - 'f0' attribute of 'ph' element
     *        Expected format: "(5,248)(47,258)(100,433)"
     * @return a HashMap which contains f0 specifications
     *         it returns empty map if given attribute is not in expected format
     */
    private Map<Integer, Integer> getPhoneF0Data(String attribute) {

        assert attribute != null;
        assert !"".equals(attribute) : "given attribute should not be empty string";

        Map<Integer, Integer> f0Map = new HashMap<Integer, Integer>();
        Pattern p = Pattern.compile("(\\d+,\\d+)");

        // Split input with the pattern
        Matcher m = p.matcher(attribute);
        while ( m.find() ) {
            String[] f0Values = (m.group().trim()).split(",");
            f0Map.put(new Integer(f0Values[0]), new Integer(f0Values[1]));
        }

        //attribute.split(regex)
        return f0Map;

    }
    
    

    /**
     * mapping a fixed value to a relative value
     * @param pitchAttribute
     * @param baseF0Contour
     * @return
     */
    private String fixedValue2RelativeValue(String pitchAttribute, double[] baseF0Contour) {
        
        pitchAttribute = pitchAttribute.substring(0,pitchAttribute.length()-2);
        double fixedValue = (new Float(pitchAttribute)).doubleValue();
        double meanValue = MathUtils.mean(baseF0Contour);
        double relative = (100.0 * fixedValue) / meanValue; 
        if ( relative > 100 ) {
            return "+"+ df.format((relative - 100)) + "%";
        }
        
        return "-"+ df.format((100 - relative)) + "%";
    }
    

    /**
     * mapping a positive 'rate' integer to a relative value 
     * @param rateAttribute
     * @return
     */
    private String positiveInteger2RelativeValues(String rateAttribute) {
        
        double positiveNumber = (new Float(rateAttribute)).doubleValue();
        double relativePercentage = (positiveNumber * 100.0);
        
        if ( relativePercentage > 100 ) {
            return "+"+ df.format((relativePercentage - 100)) + "%";
        }
        
        return "-"+ df.format((100 - relativePercentage)) + "%";
    } 

    /**
     * a look-up table for mapping rate labels to relative values
     * @param rateAttribute
     * @return
     */
    private String rateLabels2RelativeValues(String rateAttribute) {
        
        if (rateAttribute.equals("x-slow")) {
            return "-50%";
        }
        else if (rateAttribute.equals("slow")) {
            return "-33.3%";
        }
        else if (rateAttribute.equals("medium")) {
            return "+0%";
        }
        else if (rateAttribute.equals("fast")) {  
            return "+33%";  
        }
        else if (rateAttribute.equals("x-fast")) {
            return "+100%";
        }
        
        return "+0%";
    }

    /**
     * a look-up for pitch labels to relative changes 
     * @param pitchAttribute
     * @return
     */
    private String pitchLabels2RelativeValues(String pitchAttribute) {
        
        if (pitchAttribute.equals("x-low")) {
            return "-50%";
        }
        else if (pitchAttribute.equals("low")) {
            return "-25%";
        }
        else if (pitchAttribute.equals("medium")) {
            return "+0%";
        }
        else if (pitchAttribute.equals("high")) {  
            return "+100%";  
        }
        else if (pitchAttribute.equals("x-high")) {
            return "+200%";
        }
        
        return "+0%";
    }


}
