package marytts.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import marytts.client.http.Address;
import marytts.util.data.audio.AudioPlayer;

public class TestMaryClient {

    /**
     * @param args
     * @throws IOException 
     * @throws UnsupportedAudioFileException 
     */
    public static void main(String[] args) throws IOException, UnsupportedAudioFileException {
        // TODO Auto-generated method stub
        String serverHost = System.getProperty("server.host", "localhost");
        int serverPort = Integer.getInteger("server.port", 59125).intValue();
        MaryClient maryClient = MaryClient.getMaryClient(new Address(serverHost, serverPort));
        
        String locale = "en-US"; // or US English (en-US), Telugu (te), Turkish (tr), ...
        String inputType = "TEXT";
        String outputType = "AUDIO";
        String audioType = "WAVE";
        String defaultVoiceName = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String text = "I am a good boy";
        maryClient.process(text, inputType, outputType, locale, audioType, defaultVoiceName, baos);
            // The byte array constitutes a full wave file, including the headers.
            // And now, play the audio data:
        AudioInputStream ais = AudioSystem.getAudioInputStream(
                new ByteArrayInputStream(baos.toByteArray()));
       /* AudioFormat originalAF = ais.getFormat();
        AudioFormat newAF = new AudioFormat(originalAF.getSampleRate(), originalAF.getSampleSizeInBits(), originalAF.getChannels(), Boolean.TRUE, Boolean.TRUE);
        if (newAF.matches(originalAF)) {System.out.println("Matched true");}
        //AudioInputStream bigendAIS;
        //if (AudioSystem.isConversionSupported(newAF, originalAF)) {
            AudioInputStream bigendAIS = AudioSystem.getAudioInputStream(newAF, ais);
          //      System.out.println("Conversion possible!!!!");
           //   }
        */
            
            // ** To convert into bigendian format **
            AudioFormat originalAF = ais.getFormat();
            AudioInputStream bigendAIS;

            if ( originalAF.isBigEndian() ) {
                bigendAIS = ais; // no need for conversion
            }
            else{ // need conversion
                AudioFormat newAF = new AudioFormat(originalAF.getSampleRate(), originalAF.getSampleSizeInBits(), originalAF.getChannels(), Boolean.TRUE, Boolean.TRUE);
                bigendAIS = AudioSystem.getAudioInputStream(newAF, ais);
            }    
        System.out.println("Format: "+ bigendAIS.getFormat().isBigEndian());
        AudioPlayer ap = new AudioPlayer(ais);
        ap.run();  
        

    }

}
