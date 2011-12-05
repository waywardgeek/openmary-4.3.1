

/**   
*           The HMM-Based Speech Synthesis System (HTS)             
*                       HTS Working Group                           
*                                                                   
*                  Department of Computer Science                   
*                  Nagoya Institute of Technology                   
*                               and                                 
*   Interdisciplinary Graduate School of Science and Engineering    
*                  Tokyo Institute of Technology                    
*                                                                   
*                Portions Copyright (c) 2001-2006                       
*                       All Rights Reserved.
*                         
*              Portions Copyright 2000-2007 DFKI GmbH.
*                      All Rights Reserved.                  
*                                                                   
*  Permission is hereby granted, free of charge, to use and         
*  distribute this software and its documentation without           
*  restriction, including without limitation the rights to use,     
*  copy, modify, merge, publish, distribute, sublicense, and/or     
*  sell copies of this work, and to permit persons to whom this     
*  work is furnished to do so, subject to the following conditions: 
*                                                                   
*    1. The source code must retain the above copyright notice,     
*       this list of conditions and the following disclaimer.       
*                                                                   
*    2. Any modifications to the source code must be clearly        
*       marked as such.                                             
*                                                                   
*    3. Redistributions in binary form must reproduce the above     
*       copyright notice, this list of conditions and the           
*       following disclaimer in the documentation and/or other      
*       materials provided with the distribution.  Otherwise, one   
*       must contact the HTS working group.                         
*                                                                   
*  NAGOYA INSTITUTE OF TECHNOLOGY, TOKYO INSTITUTE OF TECHNOLOGY,   
*  HTS WORKING GROUP, AND THE CONTRIBUTORS TO THIS WORK DISCLAIM    
*  ALL WARRANTIES WITH REGARD TO THIS SOFTWARE, INCLUDING ALL       
*  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS, IN NO EVENT   
*  SHALL NAGOYA INSTITUTE OF TECHNOLOGY, TOKYO INSTITUTE OF         
*  TECHNOLOGY, HTS WORKING GROUP, NOR THE CONTRIBUTORS BE LIABLE    
*  FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY        
*  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,  
*  WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTUOUS   
*  ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR          
*  PERFORMANCE OF THIS SOFTWARE.                                    
*                                                                   
*/

package marytts.modules;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Vector;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.swing.text.Document;


import marytts.datatypes.MaryData;
import marytts.datatypes.MaryDataType;
import marytts.datatypes.MaryXML;
import marytts.exceptions.SynthesisException;
import marytts.features.FeatureDefinition;
import marytts.features.FeatureVector;
import marytts.htsengine.HMMData;
import marytts.htsengine.HMMVoice;
import marytts.htsengine.HTSModel;
import marytts.htsengine.HTSParameterGeneration;
import marytts.htsengine.CartTreeSet;
import marytts.htsengine.HTSUttModel;
import marytts.htsengine.HTSVocoder;
import marytts.htsengine.HTSEngineTest.PhonemeDuration;
import marytts.modules.synthesis.Voice;
import marytts.signalproc.analysis.PitchReaderWriter;
import marytts.unitselection.select.Target;
import marytts.util.MaryUtils;
import marytts.util.data.audio.AppendableSequenceAudioInputStream;
import marytts.util.data.audio.AudioPlayer;
import marytts.util.dom.MaryDomUtils;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.NodeIterator;

import marytts.signalproc.analysis.*;


/**
 * HTSEngine: a compact HMM-based speech synthesis engine.
 * 
 * Java port and extension of HTS engine version 2.0
 * Extension: mixed excitation
 * @author Marc Schr&ouml;der, Marcela Charfuelan 
 */
public class HTSEngine extends InternalModule
{
    private Logger loggerHts = MaryUtils.getLogger("HTSEngine");
    private String realisedDurations;  // HMM realised duration to be save in a file
    private boolean phoneAlignmentForDurations;
    private boolean stateAlignmentForDurations=false;   
    private Vector<PhonemeDuration> alignDur=null;  // list of external duration per phone for alignment
                                                    // this are durations loaded from a external file
    private double newStateDurationFactor = 0.5;   // this is a factor that extends or shrinks the duration of a state
                                                   // it can be used to try to syncronise the duration specified in a external file
                                                   // and the number of frames in a external lf0 file
    
    public String getRealisedDurations(){ return realisedDurations; }
    public boolean getPhonemeAlignmentForDurations(){ return phoneAlignmentForDurations; }
    public boolean getStateAlignmentForDurations(){ return stateAlignmentForDurations;}    
    public Vector<PhonemeDuration> getAlignDurations(){ return alignDur; }
    public double getNewStateDurationFactor(){ return newStateDurationFactor; }
  
    public void setRealisedDurations(String str){ realisedDurations=str; }
    public void setStateAlignmentForDurations(boolean bval){ stateAlignmentForDurations=bval; }
    public void setPhonemeAlignmentForDurations(boolean bval){ phoneAlignmentForDurations=bval; }    
    public void setAlignDurations(Vector<PhonemeDuration> val){ alignDur = val; }
    public void setNewStateDurationFactor(double dval){ newStateDurationFactor=dval; }
    
     
    public HTSEngine()
    {
        super("HTSEngine",
              MaryDataType.TARGETFEATURES,
              MaryDataType.AUDIO,
              null);
        phoneAlignmentForDurations=false;
        stateAlignmentForDurations=false;
        alignDur = null;       
    }

    /**
     * This module is actually tested as part of the HMMSynthesizer test,
     * for which reason this method does nothing.
     */
    public synchronized void powerOnSelfTest() throws Error
    {
    }
    
    
    
    /**
     * This functions process directly the target features list: targetFeaturesList
     * when using external prosody, duration and f0 are read from acoustparams: segmentsAndBoundaries
     * realised durations and f0 are set in: tokensAndBoundaries
     * when calling this function HMMVoice must be initialised already, that is TreeSet and ModelSet must be loaded already.
     * @param d : to get the default voice and locale
     * @param targetFeaturesList : 
     * @param tokensAndBoundaries
     * @return
     * @throws Exception
     */        
    public MaryData process(MaryData d, List<Target> targetFeaturesList, List<Element> segmentsAndBoundaries, List<Element> tokensAndBoundaries)
    throws Exception
    {
        /** The utterance model, um, is a Vector (or linked list) of Model objects. 
         * It will contain the list of models for current label file. */
        HTSUttModel um = new HTSUttModel();
        HTSParameterGeneration pdf2par = new HTSParameterGeneration();
        HTSVocoder par2speech = new HTSVocoder();
        AudioInputStream ais;
              
        Voice v = d.getDefaultVoice(); /* This is the way of getting a Voice through a MaryData type */
        assert v instanceof HMMVoice;
        HMMVoice hmmv = (HMMVoice)v;
        
        //String context = d.getPlainText();
        //System.out.println("TARGETFEATURES:" + context);
              
        /* Process label file of Mary context features and creates UttModel um */
        processTargetList(targetFeaturesList, segmentsAndBoundaries, um, hmmv.getHMMData());

        /* Process UttModel */
        /* Generate sequence of speech parameter vectors, generate parameters out of sequence of pdf's */  
        boolean debug = false;  /* so it does not save the generated parameters. */
        pdf2par.htsMaximumLikelihoodParameterGeneration(um, hmmv.getHMMData(),"", debug);
    
        
        /* set parameters for generation: f0Std, f0Mean and length, default values 1.0, 0.0 and 0.0 */
        /* These values are fixed in HMMVoice */
        
        /* Process generated parameters */
        /* Synthesize speech waveform, generate speech out of sequence of parameters */
        ais = par2speech.htsMLSAVocoder(pdf2par, hmmv.getHMMData());
       
        MaryData output = new MaryData(outputType(), d.getLocale());
        if (d.getAudioFileFormat() != null) {
            output.setAudioFileFormat(d.getAudioFileFormat());
            if (d.getAudio() != null) {
               // This (empty) AppendableSequenceAudioInputStream object allows a 
               // thread reading the audio data on the other "end" to get to our data as we are producing it.
                assert d.getAudio() instanceof AppendableSequenceAudioInputStream;
                output.setAudio(d.getAudio());
            }
        }     
       output.appendAudio(ais);
                     
       // set the actualDurations in tokensAndBoundaries
       if(tokensAndBoundaries != null)
         setRealisedProsody(tokensAndBoundaries, um);
              
       return output;
        
    }
 
    public void setRealisedProsody(List<Element> tokensAndBoundaries, HTSUttModel um) 
    throws SynthesisException {
      int i,j, index;
      NodeList no1, no2;
      NamedNodeMap att;
      Scanner s = null;
      String line, str[];
      float totalDur = 0f; // total duration, in seconds 
      double f0[];
      HTSModel m;
      
      int numModel = 0;
            
      for (Element e : tokensAndBoundaries) {
       //System.out.println("TAG: " + e.getTagName());
       if( e.getTagName().equals(MaryXML.TOKEN) ) {
           NodeIterator nIt = MaryDomUtils.createNodeIterator(e, MaryXML.PHONE);
           Element phone;
           while ((phone = (Element) nIt.nextNode()) != null) {                        
               String p = phone.getAttribute("p");
               m = um.getUttModel(numModel++);
               
               // CHECK THIS!!!!!!!
               
               //System.out.println("realised p=" + p + "  phoneName=" + m.getPhoneName());               
               //int currentDur = m.getTotalDurMillisec();               
               totalDur += m.getTotalDurMillisec() * 0.001f;
               //phone.setAttribute("d", String.valueOf(currentDur));
               phone.setAttribute("d", m.getMaryXmlDur());
               phone.setAttribute("end", String.valueOf(totalDur));
               
               //phone.setAttribute("f0", m.getUnit_f0ArrayStr());
               phone.setAttribute("f0", m.getMaryXmlF0());
               
               
           }
       } else if( e.getTagName().contentEquals(MaryXML.BOUNDARY) ) {
           int breakindex = 0;
           try {
               breakindex = Integer.parseInt(e.getAttribute("breakindex"));
           } catch (NumberFormatException nfe) {              
           }           
           if(e.hasAttribute("duration") || breakindex >= 3) {              
             m = um.getUttModel(numModel++);
             if(m.getPhoneName().contentEquals("_")){
               int currentDur = m.getTotalDurMillisec();
               //index = ph.indexOf("_");  
               totalDur += currentDur * 0.001f;
               e.setAttribute("duration", String.valueOf(currentDur));
             }                         
           }
       } // else ignore whatever other label...     
      }     
    }
    
    public void processUttFromFile(String feaFile, HTSUttModel um, HMMData htsData) throws Exception{
        
      List<Target> targetFeaturesList = getTargetsFromFile(feaFile, htsData);
      processTargetList(targetFeaturesList, null, um, htsData);
      
    }
   
    /* For stand alone testing. */
    public AudioInputStream processStr(String context, HMMData htsData)
    throws Exception
    {
        HTSUttModel um = new HTSUttModel();
        HTSParameterGeneration pdf2par = new HTSParameterGeneration();
        HTSVocoder par2speech = new HTSVocoder();
        AudioInputStream ais;
        
        /* htsData contains:
         * data in the configuration file, .pdf file names and other parameters. 
         * After InitHMMData it contains TreeSet ts and ModelSet ms 
         * ModelSet: Contains the .pdf's (means and variances) for dur, lf0, mcp, str and mag
         *           these are all the HMMs trained for a particular voice 
         * TreeSet: Contains the tree-xxx.inf, xxx: dur, lf0, mcp, str and mag 
         *          these are all the trees trained for a particular voice. */
        
        //loggerHts.info("TARGETFEATURES:" + context);
        
        /* Process label file of Mary context features and creates UttModel um */
        List<Target> targetFeaturesList = getTargetsFromText(context, htsData);
        processTargetList(targetFeaturesList, null, um, htsData);
        //processUtt(context, um, htsData);

        /* Process UttModel */
        /* Generate sequence of speech parameter vectors, generate parameters out of sequence of pdf's */ 
        boolean debug = false;  /* so it does not save the generated parameters. */
        pdf2par.htsMaximumLikelihoodParameterGeneration(um, htsData, "", debug);
    
        /* Process generated parameters */
        /* Synthesize speech waveform, generate speech out of sequence of parameters */
        ais = par2speech.htsMLSAVocoder(pdf2par, htsData);
        
       return ais;
        
    }
  
 
    
    /** Reads the Label file, the file which contains the Mary context features,
     *  creates an scanner object and calls getTargets
     * @param LabFile
     */
    public List<Target> getTargetsFromFile(String LabFile, HMMData htsData) throws Exception {
        List<Target> targets=null;
        Scanner s = null;
        try {    
            /* parse text in label file */
            s = new Scanner(new BufferedReader(new FileReader(LabFile)));
            targets = getTargets(s, htsData);
              
        } catch (FileNotFoundException e) {
            System.err.println("FileNotFoundException: " + e.getMessage());           
        } finally {
            if (s != null)
                s.close();
        }           
        return targets;
    }
    
    /** Creates a scanner object with the Mary context features contained in Labtext
     * and calls getTargets
     * @param LabText
     */
    public List<Target> getTargetsFromText(String LabText, HMMData htsData) throws Exception {
        List<Target> targets;
        Scanner s = null;
        try {
          s = new Scanner(LabText);
          targets = getTargets(s, htsData);
        } finally {
            if (s != null)
              s.close();
        }   
        return targets;
    }
    
    
    public List<Target> getTargets(Scanner s, HMMData htsData){
        int i;
        //Scanner s = null;
        String nextLine;
        FeatureDefinition feaDef = htsData.getFeatureDefinition();
        List<Target> targets = new ArrayList<Target>();
        FeatureVector fv;
        Target t;
        /* Skip mary context features definition */
        while (s.hasNext()) {
          nextLine = s.nextLine(); 
          if (nextLine.trim().equals("")) break;
        }
        /* skip until byte values */
        int numLines=0;
        while (s.hasNext()) {
          nextLine = s.nextLine();          
          if (nextLine.trim().equals("")) break;
          numLines++;
        }
        /* get feature vectors from byte values  */
        i=0;
        while (s.hasNext()) {
          nextLine = s.nextLine();
          //System.out.println("STR: " + nextLine);                   
          fv = feaDef.toFeatureVector(0, nextLine);
          t = new Target(fv.getFeatureAsString(feaDef.getFeatureIndex("phone"), feaDef), null);
          t.setFeatureVector(fv);              
          targets.add(t);              
        }                
        return targets;
    }
    
    
 
    /***
     * Process feature vectors in target list to generate a list of models for generation and realisation
     * @param targetFeaturesList : each target must contain the corresponding feature vector
     * @param segmentsAndBoundaries : if applying external prosody provide acoust params as a list of elements
     * @param um : as a result of this process a utterance model list is created for generation and then realisation
     * @param htsData : parameters and configuration of the voice
     * @throws Exception
     */
    private void processTargetList(List<Target> targetFeaturesList, List<Element> segmentsAndBoundaries, HTSUttModel um, HMMData htsData)
    throws Exception {          
      int i, mstate,frame, k, newStateDuration;
      HTSModel m;
      CartTreeSet cart = htsData.getCartTreeSet();
      realisedDurations = "#\n";
      Integer numLab=0;
      double diffdurOld = 0.0;
      double diffdurNew = 0.0;
      double mean = 0.0;
      double var = 0.0;
      double durationsFraction;
      int alignDurSize=0;
      float fperiodmillisec = ((float)htsData.getFperiod() / (float)htsData.getRate()) * 1000;
      float fperiodsec = ((float)htsData.getFperiod() / (float)htsData.getRate());
      Integer dur;
      boolean firstPh = true; 
      boolean lastPh = false;
      float durVal = 0.0f; 
      Float durSec;
      FeatureVector fv;
      FeatureDefinition feaDef = htsData.getFeatureDefinition();
       
      if( htsData.getUseAcousticModels() ){
        phoneAlignmentForDurations = true;
        loggerHts.info("Using prosody from acoustparams.");
      } else {
        phoneAlignmentForDurations = false;
        loggerHts.info("Estimating state durations from (Gaussian) state duration model.");
      }        
      
      // process feature vectors in targetFeatureList
      i=0;
      for (Target target : targetFeaturesList) {
                    
          fv = target.getFeatureVector();  //feaDef.toFeatureVector(0, nextLine);
          um.addUttModel(new HTSModel(cart.getNumStates()));            
          m = um.getUttModel(i);
          m.setPhoneName(fv.getFeatureAsString(feaDef.getFeatureIndex("phone"), feaDef));  
          //System.out.println("phone=" + m.getPhoneName());
 
          // get the duration and f0 values from the acoustparams = segmentsAndBoundaries
          // if i can get these values from continuous features could be a lot better!!!!
          if( phoneAlignmentForDurations && segmentsAndBoundaries != null) {
            Element e = segmentsAndBoundaries.get(i);
            //System.out.print("phone=" + m.getPhoneName() + "  TagName=" + e.getTagName());
            // Determine state-level duration                      
            //if( phoneAlignmentForDurations) {  // use phone alignment for duration
            // get the durations of the Gaussians any way, because we need to know how long each estate should be
            // knowing the duration of each state we can modified it so the 5 states reflect the external duration
            diffdurNew = cart.searchDurInCartTree(m, fv, htsData, firstPh, lastPh, diffdurOld);
             
            if( e.getTagName().contentEquals("ph") ){
              m.setMaryXmlDur(e.getAttribute("d"));  
              durVal = Float.parseFloat(m.getMaryXmlDur());
              //System.out.println("  durVal=" + durVal  + " totalDurGauss=" + (fperiodmillisec * m.getTotalDur()) + "(" + m.getTotalDur() + " frames)" );              
              // get proportion of this duration for each state; m.getTotalDur() contains total duration of the 5 states in frames
              durationsFraction = durVal/(fperiodmillisec * m.getTotalDur());
              m.setTotalDur(0);              
              for(k=0; k<cart.getNumStates(); k++){
                //System.out.print("   state: " + k + " durFromGaussians=" + m.getDur(k));
                newStateDuration = (int)(durationsFraction*m.getDur(k) + newStateDurationFactor);
                if( newStateDuration <= 0 )
                  newStateDuration = 1;
                m.setDur(k, newStateDuration);
                m.setTotalDur(m.getTotalDur() + m.getDur(k)); 
                //System.out.println("   durNew=" + m.getDur(k));       
              }
                
            }
            else if( e.getTagName().contentEquals("boundary") ){  // the duration for boundaries predicted in the AcousticModeller is not calculated with HMMs
              durVal = (m.getTotalDur() * fperiodmillisec);       // it is kind of dummy val, so here we need to use the predicted from HMMs
              //System.out.println("  durVal=" + durVal);           // this will be indicated with -1
                                                                  // the actual duration is calculated below taking into account states duration
                                                                  //durVal = Float.parseFloat(e.getAttribute("duration"));
              m.setMaryXmlDur(Float.toString(durVal));  // CHECK if this boundary value is correctly set in realised_acoustparams
            }
                       
            
            // set F0 values 
            if( e.hasAttribute("f0") ){
              m.setMaryXmlF0(e.getAttribute("f0"));
              //System.out.println("   f0=" + e.getAttribute("f0"));
            }
            
          } else { // Estimate state duration from state duration model (Gaussian)                 
              diffdurNew = cart.searchDurInCartTree(m, fv, htsData, firstPh, lastPh, diffdurOld);       
          }

          um.setTotalFrame(um.getTotalFrame() + m.getTotalDur());
          //System.out.println("   model=" + m.getPhoneName() + "   TotalDurFrames=" + m.getTotalDur() + "  TotalDurMilisec=" + (fperiodmillisec * m.getTotalDur()) + "\n");
                  
          // Set realised durations 
          m.setTotalDurMillisec((int)(fperiodmillisec * m.getTotalDur()));               
                    
          durSec = um.getTotalFrame() * fperiodsec;
          realisedDurations += durSec.toString() +  " " + numLab.toString() + " " + m.getPhoneName() + "\n";
          numLab++;
          
          
          diffdurOld = diffdurNew;  // to calculate the duration of next phoneme
          
          /* Find pdf for LF0, this function sets the pdf for each state. 
           * here it is also set whether the model is voiced or not */ 
          // if ( ! htsData.getUseUnitDurationContinuousFeature() )
          // Here according to the HMM models it is decided whether the states of this model are voiced or unvoiced
          // even if f0 is taken from maryXml here we need to set the voived/unvoiced values per model and state
          cart.searchLf0InCartTree(m, fv, feaDef, htsData.getUV());    
   
          /* Find pdf for MCP, this function sets the pdf for each state.  */
          cart.searchMcpInCartTree(m, fv, feaDef);

          /* Find pdf for strengths, this function sets the pdf for each state.  */
          if(htsData.getTreeStrFile() != null)
            cart.searchStrInCartTree(m, fv, feaDef);
          
          /* Find pdf for Fourier magnitudes, this function sets the pdf for each state.  */
          if(htsData.getTreeMagFile() != null)
            cart.searchMagInCartTree(m, fv, feaDef);
          
          /* increment number of models in utterance model */
          um.setNumModel(um.getNumModel()+1);
          /* update number of states */
          um.setNumState(um.getNumState() + cart.getNumStates());
          i++;
          
          if(firstPh)
            firstPh = false;
      }
      
      if(phoneAlignmentForDurations && alignDur != null)
        if( um.getNumUttModel() != alignDurSize )
            throw new Exception("The number of durations provided for phone alignment (" + alignDurSize +
                    ") is greater than the number of feature vectors (" + um.getNumUttModel() + ")."); 

      for(i=0; i<um.getNumUttModel(); i++){
          m = um.getUttModel(i);                  
          for(mstate=0; mstate<cart.getNumStates(); mstate++)
              for(frame=0; frame<m.getDur(mstate); frame++) 
                  if(m.getVoiced(mstate))
                      um.setLf0Frame(um.getLf0Frame() +1);
          //System.out.println("Vector m[" + i + "]=" + m.getPhoneName() ); 
      }

      loggerHts.info("Number of models in sentence numModel=" + um.getNumModel() + "  Total number of states numState=" + um.getNumState());
      loggerHts.info("Total number of frames=" + um.getTotalFrame() + "  Number of voiced frames=" + um.getLf0Frame());  
      
      //System.out.println("REALISED DURATIONS:" + realisedDurations);
      
  } /* method processTargetList */

    
    /** 
     * Stand alone testing using a TARGETFEATURES file as input. 
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException, InterruptedException, Exception{
       
      int i, j; 
      /* configure log info */
      org.apache.log4j.BasicConfigurator.configure();

      /* To run the stand alone version of HTSEngine, it is necessary to pass a configuration
       * file. It can be one of the hmm configuration files in MARY_BASE/conf/*hmm*.config 
       * The input for creating a sound file is a TARGETFEATURES file in MARY format, there
       * is an example indicated in the configuration file as well.
       * For synthesising other text please generate first a TARGETFEATURES file with the MARY system
       * save it in a file and use it as feaFile. */
      HTSEngine hmm_tts = new HTSEngine();
      
      /* htsData contains:
       * Data in the configuration file, .pdf, tree-xxx.inf file names and other parameters. 
       * After initHMMData it containswhile(it.hasNext()){
            phon = it.next(); TreeSet ts and ModelSet ms 
       * ModelSet: Contains the .pdf's (means and variances) for dur, lf0, mcp, str and mag
       *           these are all the HMMs trained for a particular voice 
       * TreeSet: Contains the tree-xxx.inf, xxx: dur, lf0, mcp, str and mag 
       *          these are all the trees trained for a particular voice. */
      HMMData htsData = new HMMData();
            
      /* For initialise provide the name of the hmm voice and the name of its configuration file,*/
       
      String MaryBase    = "/project/mary/marcela/openmary/"; /* MARY_BASE directory.*/
      //String voiceName   = "roger-hsmm";                        /* voice name */
      //String voiceConfig = "en_GB-roger-hsmm.config";         /* voice configuration file name. */
      //String voiceName   = "dfki-poppy-hsmm";                        /* voice name */
      //String voiceConfig = "en_GB-dfki-poppy-hsmm.config";         /* voice configuration file name. */
      //String voiceName   = "cmu-slt-hsmm";                        /* voice name */
      //String voiceConfig = "en_US-cmu-slt-hsmm.config";         /* voice configuration file name. */
      String voiceName   = "prudence-hsmm-v7";                        /* voice name */
      String voiceConfig = "en_GB-prudence-hsmm-v7.config";         /* voice configuration file name. */
      
      //String voiceName   = "hsmm-ot";                        /* voice name */
      //String voiceConfig = "tr-hsmm-ot.config";         /* voice configuration file name. */
      String durFile     = MaryBase + "tmp/tmp.lab";          /* to save realised durations in .lab format */
      String parFile     = MaryBase + "tmp/tmp";              /* to save generated parameters tmp.mfc and tmp.f0 in Mary format */
      String outWavFile  = MaryBase + "tmp/tmp.wav";          /* to save generated audio file */
      
      // The settings for using GV and MixExc can be changed in this way:      
      htsData.initHMMData(voiceName, MaryBase, voiceConfig);
      htsData.setUseGV(true);
      htsData.setUseMixExc(true);
      htsData.setUseFourierMag(true);  // if the voice was trained with Fourier magnitudes
      
       
      /** The utterance model, um, is a Vector (or linked list) of Model objects. 
       * It will contain the list of models for current label file. */
      HTSUttModel um = new HTSUttModel();
      HTSParameterGeneration pdf2par = new HTSParameterGeneration();        
      HTSVocoder par2speech = new HTSVocoder();
      AudioInputStream ais;
               
      /** Example of context features file */
      //String feaFile = htsData.getFeaFile();
      //String feaFile = "/project/mary/marcela/HMM-voices/poppy/phonefeatures/w0130.pfeats";
      // "Accept a father's blessing, and with it, this."
      //String feaFile = "/project/mary/marcela/HMM-voices/roger/phonefeatures/roger_5739.pfeats";
      // "It seems like a strange pointing of the hand of God."
      //String feaFile = "/project/mary/marcela/HMM-voices/roger/phonefeatures/roger_5740.pfeats";
      String feaFile = "/project/mary/marcela/HMM-voices/prudence/phonefeatures/pru009.pfeats";
      
      try {
          /* Process Mary context features file and creates UttModel um, a linked             
           * list of all the models in the utterance. For each model, it searches in each tree, dur,   
           * cmp, etc, the pdf index that corresponds to a triphone context feature and with           
           * that index retrieves from the ModelSet the mean and variance for each state of the HMM.   */
          hmm_tts.processUttFromFile(feaFile, um, htsData);
        
          /* save realised durations in a lab file */             
          FileWriter outputStream = new FileWriter(durFile);
          outputStream.write(hmm_tts.realisedDurations);
          outputStream.close();
          

          /* Generate sequence of speech parameter vectors, generate parameters out of sequence of pdf's */
          /* the generated parameters will be saved in tmp.mfc and tmp.f0, including Mary header. */
          boolean debug = true;  /* so it save the generated parameters in parFile */
          pdf2par.htsMaximumLikelihoodParameterGeneration(um, htsData, parFile, debug);
          
          /* Synthesize speech waveform, generate speech out of sequence of parameters */
          ais = par2speech.htsMLSAVocoder(pdf2par, htsData);
     
          System.out.println("Saving to file: " + outWavFile);
          System.out.println("Realised durations saved to file: " + durFile);
          File fileOut = new File(outWavFile);
          
          if (AudioSystem.isFileTypeSupported(AudioFileFormat.Type.WAVE,ais)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, fileOut);
          }

          System.out.println("Calling audioplayer:");
          AudioPlayer player = new AudioPlayer(fileOut);
          player.start();  
          player.join();
          System.out.println("Audioplayer finished...");
   
     
      } catch (Exception e) {
          System.err.println("Exception: " + e.getMessage());
      }
    }  /* main method */
    
    
  
}  /* class HTSEngine*/

