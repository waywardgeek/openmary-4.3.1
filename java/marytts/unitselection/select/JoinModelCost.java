/**
 * Copyright 2006 DFKI GmbH.
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
package marytts.unitselection.select;

import java.io.IOException;

import marytts.exceptions.MaryConfigurationException;
import marytts.features.FeatureDefinition;
import marytts.features.FeatureVector;
import marytts.htsengine.PhoneTranslator;
import marytts.cart.CART;
import marytts.cart.Node;
import marytts.cart.LeafNode.PdfLeafNode;
import marytts.cart.io.HTSCARTReader;
import marytts.server.MaryProperties;
import marytts.signalproc.analysis.distance.DistanceComputer;
import marytts.unitselection.data.DiphoneUnit;
import marytts.unitselection.data.Unit;


public class JoinModelCost implements JoinCostFunction
{
    protected int nCostComputations = 0;
    
    /****************/
    /* DATA FIELDS  */
    /****************/
    private JoinCostFeatures jcf = null;
    
    CART[] joinTree = null;  // an array of carts, one per HMM state.
    
    private float f0Weight;
    
    private FeatureDefinition featureDef = null;
    
    private boolean debugShowCostGraph = false;
   
    
    /****************/
    /* CONSTRUCTORS */
    /****************/

    /**
     * Empty constructor; when using this, call load() separately to 
     * initialise this class.
     * @see #load(String)
     */
    public JoinModelCost()
    {
    }
    
    /**
     * Initialise this join cost function by reading the appropriate settings
     * from the MaryProperties using the given configPrefix.
     * @param configPrefix the prefix for the (voice-specific) config entries
     * to use when looking up files to load.
     */
    public void init(String configPrefix) throws MaryConfigurationException
    {
        String joinFileName = MaryProperties.needFilename(configPrefix+".joinCostFile");
        String joinPdfFileName = MaryProperties.needFilename(configPrefix + ".joinPdfFile");
        String joinTreeFileName = MaryProperties.needFilename(configPrefix + ".joinTreeFile");
        //CHECK not tested the trickyPhonesFile needs to be added into the configuration file
        String trickyPhonesFileName = MaryProperties.needFilename(configPrefix + ".trickyPhonesFile");
        try {
            load(joinFileName, joinPdfFileName, joinTreeFileName, trickyPhonesFileName);
        } catch (IOException ioe) {
            throw new MaryConfigurationException("Problem loading join file "+joinFileName, ioe);
        }
    }
    
    @Deprecated
    public void load(String a, String b, String c, float d)
    {
        throw new RuntimeException("Do not use load() -- use init()");
    }
    
    
   /**
     * Load weights and values from the given file
     * @param joinFileName the file from which to read join cost features
     * @param joinPdfFileName the file from which to read the Gaussian models in the leaves of the tree
     * @param joinTreeFileName the file from which to read the Tree, in HTS format.
     */
    public void load(String joinFileName, String joinPdfFileName, String joinTreeFileName, String trickyPhonesFile)
    throws IOException, MaryConfigurationException
    {
        jcf = new JoinCostFeatures(joinFileName);

        assert featureDef != null : "Expected to have a feature definition, but it is null!";
      
        /* Load Trees */
        HTSCARTReader htsReader = new HTSCARTReader();
        int numStates = 1;  // just one state in the joinModeller
        
        // Check if there are tricky phones, and create a PhoneTranslator object
        PhoneTranslator phTranslator = new PhoneTranslator(trickyPhonesFile);
        
        try {
            //joinTree.loadTreeSetGeneral(joinTreeFileName, 0, featureDef);
            joinTree = htsReader.load(numStates, joinTreeFileName, joinPdfFileName, featureDef, phTranslator);
            
        } catch (Exception e) {
            IOException ioe = new IOException("Cannot load join model trees from "+joinTreeFileName);
            ioe.initCause(e);
            throw ioe;
        }
                

    }
    
    /**
     * Set the feature definition to use for interpreting target feature vectors.
     * @param def the feature definition to use.
     */
    public void setFeatureDefinition(FeatureDefinition def)
    {
        this.featureDef = def;
    }
    
    /*****************/
    /* MISC METHODS  */
    /*****************/

    /**
     * A combined cost computation, as a weighted sum
     * of the signal-based cost (computed from the units)
     * and the phonetics-based cost (computed from the targets).
     * 
     * @param t1 The left target.
     * @param u1 The left unit.
     * @param t2 The right target.
     * @param u2 The right unit.
     * 
     * @return the cost of joining the left unit with the right unit, as a non-negative value.
     */
    public double cost(Target t1, Unit u1, Target t2, Unit u2 ) {
        // Units of length 0 cannot be joined:
        if (u1.duration == 0 || u2.duration == 0) return Double.POSITIVE_INFINITY;
        // In the case of diphones, replace them with the relevant part:
        if (u1 instanceof DiphoneUnit) {
            u1 = ((DiphoneUnit)u1).right;
        }
        if (u2 instanceof DiphoneUnit) {
            u2 = ((DiphoneUnit)u2).left;
        }
        
        if (u1.index+1 == u2.index) return 0;
        double cost = 1; // basic penalty for joins of non-contiguous units. 
        
        float[] v1 = jcf.getRightJCF(u1.index);
        float[] v2 = jcf.getLeftJCF(u2.index);
        //double[] diff = new double[v1.length];
        //for ( int i = 0; i < v1.length; i++ ) {
        //    diff[i] = (double)v1[i] - v2[i];
        //}
        double[] diff = new double[v1.length];
        for ( int i = 0; i < v1.length; i++ ) {
            diff[i] = (double)v1[i] - v2[i];
        }
                
        // Now evaluate likelihood of the diff under the join model
        // Compute the model name:
        assert featureDef != null : "Feature Definition was not set";
        FeatureVector fv1 = null;
        if (t1 instanceof DiphoneTarget) {
            HalfPhoneTarget hpt1 = ((DiphoneTarget)t1).right;
            assert hpt1 != null;
            fv1 = hpt1.getFeatureVector();
        } else {
            fv1 = t1.getFeatureVector();
        }
        assert fv1 != null : "Target has no feature vector";
        //String modelName = contextTranslator.features2context(featureDef, fv1, featureList);
       
        int state = 0;  // just one state in the joinModeller
        double[] mean;
        double[] variance;
        
        Node node = joinTree[state].interpretToNode(fv1, 1);
        
        assert node instanceof PdfLeafNode : "The node must be a PdfLeafNode.";
        
        mean = ((PdfLeafNode)node).getMean();
        variance = ((PdfLeafNode)node).getVariance();
        
        double distance = DistanceComputer.getNormalizedEuclideanDistance(diff, mean, variance);
        
        cost += distance;

        return cost;
    }
    
    
    
    
    
    

}

