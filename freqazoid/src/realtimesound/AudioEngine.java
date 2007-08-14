/*
 * AudioEngine.java
 *
 * Created on March 24, 2007, 2:12 PM
 *
 */
package realtimesound;

import java.io.File;
import java.io.IOException;

import gui.ResourceManager;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

public class AudioEngine implements Runnable {
    
	private ResourceManager rm;
	
    private static final int SAMPLE_RATE = 44100;
    private static final int BIT_DEPTH = 16;
    private static final int N_CHANNELS = 1;
    private static final boolean LITTLE_ENDIAN = false;
    private AudioFormat format;
    
    private static final int BUFFER_SIZE = 4096*8;    
    private TargetDataLine inputLine;
    private SourceDataLine outputLine;  
    private String[] inputInfos;
    private String[] outputInfos;
    
    private static final int BLOCK_SIZE = 1024*8;
    
    public static final int STARTING = 0, RUNNING = 1, 
    						PAUSED = 2, STOPPING = 3, STOPPED = 4;    
    private int engineStatus;    
    
    private AudioAnalyser audioAnalyser;
    
    private AudioInputStream inputFileStream;
    private AudioFileFormat inputFileFormat;
    private File inputFile;
    
    public boolean muteMicrophone;
    public boolean muteFile;
    
    public AudioEngine(ResourceManager rm) {
        this.rm = rm;
        
        muteMicrophone = false;
        muteFile = true;
        
        engineStatus = STOPPED;
        int frameSizeInBytes = BIT_DEPTH/8;
        int frameRate = SAMPLE_RATE;
        format = new  AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE, BIT_DEPTH, N_CHANNELS, frameSizeInBytes, frameRate, LITTLE_ENDIAN);
        
        System.out.println("audio format: "+format.toString());
        
        // Find the Mixer's in the computer        
        Mixer.Info[] info = AudioSystem.getMixerInfo();
        Line.Info[] lineInfo = AudioSystem.getSourceLineInfo(new Line.Info(SourceDataLine.class));
        
        for(int i=0; i<lineInfo.length; i++) {
        	//AudioSystem.getSourceDataLine(arg0, arg1)
        	//System.out.println(lineInfo[i]);
        }
        
        inputInfos = new String[info.length];
        outputInfos = new String[info.length];
        int n=0;
        int m=0;
        for(int i=0; i<info.length; i++) {
        	if(AudioSystem.getMixer(info[i]).getSourceLineInfo().length > 0) {        		
        		inputInfos[n] = n+": "+info[i].getName() +": "+info[i].getDescription();
        		n++;
        	}
        	if(AudioSystem.getMixer(info[i]).getTargetLineInfo().length > 0) {
        		outputInfos[m] = m+": "+info[i].getName() +": "+info[i].getDescription();
        		m++;
        	}        	
        	//System.out.println(info[i].getName()+"::"+info[i].getDescription());
        	//System.out.println(mixerInfo[i]);
        }
        
        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);        
        DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, format);
        
        if(AudioSystem.isLineSupported(targetInfo)) {
//            System.out.println("input format is supported by the system");
            try {
//                System.out.println("trying to open an input line...");
                inputLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
                inputLine.open(format, BUFFER_SIZE);
//                System.out.println("Input line opened with a buffer size: "
//                        + inputLine.getBufferSize());
            } catch (LineUnavailableException ex) {
                ex.printStackTrace();
            }            
        }
        
        if(AudioSystem.isLineSupported(sourceInfo)) {
//            System.out.println("output format supported by the system");
            try {
//                System.out.println("trying to open an output line...");
                outputLine = (SourceDataLine) AudioSystem.getLine(sourceInfo);
                outputLine.open(format, BUFFER_SIZE);
//                System.out.println("Output line opened with a buffer size: "
//                        + inputLine.getBufferSize());
            } catch (LineUnavailableException ex) {
                ex.printStackTrace();
            }            
        }
        
        audioAnalyser = new AudioAnalyser();
    }
    
    public void run()  {
        int numBytesRead;
        int numBytesWritten;        
        byte[] dataFromMic = new byte[BLOCK_SIZE*2];
        byte[] dataFromFile = new byte[BLOCK_SIZE*2];
        byte[] dataMasterOut = new byte[BLOCK_SIZE*2];
        
        inputLine.start();
        outputLine.start();
//        System.out.println("Engine started.");
        engineStatus = RUNNING;        
        
        while(true) {
            switch(engineStatus) {
            	case STARTING:
            		try {
						inputLine.open(format, BUFFER_SIZE);
						outputLine.open(format, BUFFER_SIZE);
					} catch (LineUnavailableException e) {
						e.printStackTrace();
					}
					
            		inputLine.start();
                    outputLine.start();
                    engineStatus = RUNNING;
                    System.out.println("Engine started.");
                    break;
                case RUNNING:
                
                if( inputLine.available() > BLOCK_SIZE*2 )
                {
                	// Read the next chunk of data from the TargetDataLine.
                	numBytesRead =  inputLine.read(dataFromMic, 0, dataFromMic.length);
                	
                	if ( !muteFile && inputFile != null ) {
                		try {
							inputFileStream.read(dataFromFile);
                		} catch (IOException e) {
							e.printStackTrace();
						}
                	}              
                	
                	/* plot graph
                	 * This contradicts with the encapsulation idea. AudioEngine must have
                	 * no idea about the interface to plot the audio.
                	 * */
                	int[] masterOut = new int[BLOCK_SIZE];                	
                	
                	for(int i=0, j = 0; j<BLOCK_SIZE; i+=2, j++) {
                		masterOut[j] = 0;
                		if( !muteMicrophone ) {
                			masterOut[j] += ((dataFromMic[i] & 0xFF) | (dataFromMic[i+1]<<8));                	
                		}
                		if( !muteFile ) {
                			masterOut[j] += (dataFromFile[i] & 0xFF) | (dataFromFile[i+1]<<8);
                		}
//                		rm.getCanvas().setData( masterOut[j] );                    
                	}                	
                	audioAnalyser.addSamples(masterOut);
//                	for(int j=0; j<masterOut.length; j++) {
//    					System.out.print(masterOut[j]+", ");
//    				}
//    				System.out.print("\n");
    				
                	
                	for(int i=0, j=0; j<BLOCK_SIZE; i+=2, j++) {                		
                		dataMasterOut[i]   = (byte)(masterOut[j] &  0xFF);
                		dataMasterOut[i+1] = (byte)(masterOut[j] >> 8);
                	}     	
                	
                	numBytesWritten = outputLine.write(dataMasterOut, 0, dataMasterOut.length);
            
                	//numBytestoRead= outputLine.available();
                	//System.out.println(numBytesWritten);
                }
            
                break;
                case PAUSED:
                    break;
                case STOPPING:
                    //inputLine.drain();
                    System.out.println("stopping input line");
                    inputLine.stop();
                    System.out.println("closing input line");
                    inputLine.close();
                    System.out.println("Engine stopped.");
                    engineStatus = STOPPED;
                    break;
                case STOPPED:
                	break;
            }    
        }
    }
    
    public void openFile(File file) {
    	inputFile = file;
//        System.out.println("can read the file? "+ file.canRead() );
        try {
			inputFileStream = AudioSystem.getAudioInputStream(file);
			inputFileFormat = AudioSystem.getAudioFileFormat(file);
			
//			System.out.println("mark supported? "+ inputFileStream.markSupported() );
		} catch (UnsupportedAudioFileException e) {
			System.out.println("unsupported file format");
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(inputFileFormat.toString());
    }
    
    public void reopenFile() {
    	if(inputFile !=  null ) {
    		openFile(inputFile);
    	}
    }
    
    public int getEngineStatus() {
        return engineStatus;
    }
    
    public void setEngineStatus(int s) {
        engineStatus = s;
    }
    
    public void pauseEngine() {
        if(engineStatus == RUNNING) {
            engineStatus = PAUSED;
            // Tikkayt, drain read edilmemisse beele bekliyor...            
            //inputLine.drain();
            System.out.println("pausing");
            inputLine.stop();
            //System.out.println("patlican");
            //inputLine.close();
            engineStatus = PAUSED;
        }
        else if(engineStatus == PAUSED) {
            System.out.println("unpausing");
            engineStatus = RUNNING;
            inputLine.start();
        }        
    }
    
    public void stopEngine() {
        engineStatus = STOPPING;
    }
    
    public void startEngine() {
    	engineStatus = STARTING;
    }
    
    public String[] getInputInfos() {
    	return inputInfos;
    }
    
    public String[] getOutputInfos() {
    	return outputInfos;
    }

	public AudioAnalyser getAudioAnalyser() {
		return audioAnalyser;
	}
	
	
    
}