/**
 * Copyright 2007 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * Permission is hereby granted, free of charge, to use and distribute
 * this software and its documentation without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of this work, and to
 * permit persons to whom this work is furnished to do so, subject to
 * the following conditions:
 * 
 * 1. The code must retain the above copyright notice, this list of
 *    conditions and the following disclaimer.
 * 2. Any modifications must be clearly marked as such.
 * 3. Original authors' names are not deleted.
 * 4. The authors' names are not used to endorse or promote products
 *    derived from this software without specific prior written
 *    permission.
 *
 * DFKI GMBH AND THE CONTRIBUTORS TO THIS WORK DISCLAIM ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL DFKI GMBH NOR THE
 * CONTRIBUTORS BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
 * ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
 * THIS SOFTWARE.
 */

package marytts.signalproc.adaptation;

import java.util.Arrays;

import marytts.signalproc.adaptation.gmm.jointgmm.JointGMM;
import marytts.signalproc.adaptation.gmm.jointgmm.JointGMMMatch;
import marytts.signalproc.analysis.Labels;
import marytts.util.math.ArrayUtils;
import marytts.util.math.MathUtils;
import marytts.util.signal.SignalProcUtils;

/**
 * @author oytun.turk
 *
 */
public class TargetLsfCopyMapper extends VocalTractTransformationFunction {

    public TargetLsfCopyMapper()
    {
        
    }
    
    public LsfMatch transform(double sourceTime, Labels sourceLabels, Labels targetLabels, double[][] targetLsfs, double targetLsfAnalysisWindowSizeInSeconds, double targetLsfAnalysisSkipSizeInSeconds)
    {
        double targetTime = SignalProcUtils.sourceTime2targetTime(sourceTime, sourceLabels, targetLabels);
        
        int targetInd = SignalProcUtils.time2frameIndex(targetTime, targetLsfAnalysisWindowSizeInSeconds, targetLsfAnalysisSkipSizeInSeconds);
        
        targetInd = MathUtils.CheckLimits(targetInd, 0, targetLsfs.length-1);
        
        return new LsfMatch(targetLsfs[targetInd]);
    }
}
