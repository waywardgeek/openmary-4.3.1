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
package marytts.tests.junit4;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import marytts.datatypes.MaryXML;
import marytts.util.data.audio.MaryAudioUtils;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.DocumentTraversal;


/**
 * @author Marc Schr&ouml;der
 *
 *
 */
public class EnvironmentTests {

    @Test
    public void testJavaVersion() {
        String version = System.getProperty("java.version");
        assertTrue(version.startsWith("1.5") || version.startsWith("1.6"));
    }

    @Test
    public void testXMLParserSupportsNamespaces() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder docBuilder = factory.newDocumentBuilder();
        Document document =
            docBuilder.parse(
                this.getClass().getResourceAsStream("test1.namespaces"));
        NodeList nl =
            document.getElementsByTagNameNS(
                "http://www.w3.org/2001/10/synthesis",
                "*");
        assertNotNull(nl.item(0));
        assertTrue(nl.item(0).getNodeName().equals("ssml:emphasis"));
    }

    @Test
    public void testDocumentTraversalAvailable() {
        Document doc = MaryXML.newDocument();
        assertTrue(doc instanceof DocumentTraversal);
    }

    @Test
    public void testMP3Available() throws Exception {
        AudioFormat mp3af = new AudioFormat(
                new AudioFormat.Encoding("MPEG1L3"),
                AudioSystem.NOT_SPECIFIED,
                AudioSystem.NOT_SPECIFIED,
                1,
                AudioSystem.NOT_SPECIFIED,
                AudioSystem.NOT_SPECIFIED,
                false);
        AudioInputStream waveStream = AudioSystem.getAudioInputStream(this.getClass().getResourceAsStream("test.wav"));
        // Now attempt conversion:
        if (MaryAudioUtils.canCreateMP3()) {
            assertTrue(AudioSystem.isConversionSupported(mp3af, waveStream.getFormat()));
            AudioInputStream mp3Stream = AudioSystem.getAudioInputStream(mp3af, waveStream);
        } else {
            assertFalse(AudioSystem.isConversionSupported(mp3af, waveStream.getFormat()));
        }
    }
}

