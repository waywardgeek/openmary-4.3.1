/**
 * Copyright 2004-2006 DFKI GmbH.
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
package marytts.tests.junit4;


import org.junit.Assert;
import org.junit.Test;

import marytts.util.data.BufferedDoubleDataSource;
import marytts.util.math.MathUtils;

/**
 * @author Marc Schr&ouml;der
 *
 */
public class BufferedDoubleDataSourceTest
{
    @Test
    public void testGetAllData1()
    {
        double[] signal = FFTTest.getSampleSignal(10000);
        double[] result = new BufferedDoubleDataSource(signal).getAllData();
        Assert.assertTrue(signal.length == result.length);
    }
    
    @Test
    public void testGetAllData2()
    {
        double[] signal = FFTTest.getSampleSignal(10000);
        double[] result = new BufferedDoubleDataSource(signal).getAllData();
        Assert.assertTrue(MathUtils.sumSquaredError(signal, result)<1.E-20);
    }
    

}

