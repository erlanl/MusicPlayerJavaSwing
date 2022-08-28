import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

public class Player{

    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;
    /**
     * The MPEG audio decoder.
     */
    private Decoder decoder;
    /**
     * The AudioDevice where audio samples are written to.
     */
    private AudioDevice device;

    private PlayerWindow window;

    //Variavel teste
    private String[][] listaString = new String[0][];
    private Song[] listaSong = new Song[0];

    private int currentFrame = 0;
    private SwingWorker thr;

    public static String[][] removeElement( String [][] arr, int index ){
        String[][] arrDestination = new String[arr.length - 1][];
        int remainingElements = arr.length - ( index + 1 );
        System.arraycopy(arr, 0, arrDestination, 0, index);
        System.arraycopy(arr, index + 1, arrDestination, index, remainingElements);
        return arrDestination;
    }

    public static Song[] removeElementSong( Song [] arr, int index ){
        Song[] arrDestination = new Song[arr.length - 1];
        int remainingElements = arr.length - ( index + 1 );
        System.arraycopy(arr, 0, arrDestination, 0, index);
        System.arraycopy(arr, index + 1, arrDestination, index, remainingElements);
        return arrDestination;
    }


    private final ActionListener buttonListenerPlayNow = e -> {

        if(thr != null){
            thr.cancel(true);
            currentFrame = 0;
        }

        thr = new SwingWorker() {
        @Override
        protected Object doInBackground() throws Exception {
            int index = window.getIndex(listaString);
            window.setPlayingSongInfo(listaSong[index].getTitle(), listaSong[index].getAlbum(), listaSong[index].getArtist());
            if(bitstream != null){
                try {
                    bitstream.close();
                } catch (BitstreamException ex) {
                    throw new RuntimeException(ex);
                }

                device.close();
            }

            try {
                device = FactoryRegistry.systemRegistry().createAudioDevice();
            } catch (JavaLayerException ex) {}

            try {
                device.open(decoder = new Decoder());
            } catch (JavaLayerException ex) {}

            try {
                bitstream = new Bitstream(listaSong[window.getIndex(listaString)].getBufferedInputStream());
            } catch (FileNotFoundException ex) {}

            while (playNextFrame()) {
                window.setTime((int) (currentFrame*listaSong[index].getMsPerFrame()) ,(int) (listaSong[index].getNumFrames()*listaSong[index].getMsPerFrame()));
                currentFrame++;
            }

            currentFrame = 0;
            window.resetMiniPlayer();
            return null;
        }
    };
        thr.execute();
    };

    private final ActionListener buttonListenerRemove = e -> {
        int index = window.getIndex(listaString);
        listaString = removeElement(listaString, index);

        this.window.setQueueList(listaString);

        listaSong = removeElementSong(listaSong, index);

    };
    private final ActionListener buttonListenerAddSong = e -> {
        Song novo;
        try {
            novo = this.window.openFileChooser();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } catch (BitstreamException ex) {
            throw new RuntimeException(ex);
        } catch (UnsupportedTagException ex) {
            throw new RuntimeException(ex);
        } catch (InvalidDataException ex) {
            throw new RuntimeException(ex);
        }

        if (novo != null) {//fazendo a matriz onde cada posição é uma musica com array dinamic
            int N = listaString.length;
            listaString = Arrays.copyOf(listaString, N + 1);
            listaString[N] = novo.getDisplayInfo();

            this.window.setQueueList(listaString);
            //"array dinamico"
            int N2 = listaSong.length;
            listaSong = Arrays.copyOf(listaSong, N2 + 1);
            listaSong[N2] = novo;
        }
    };

    private final ActionListener buttonListenerPlayPause = e -> {};
    private final ActionListener buttonListenerStop = e -> {};
    private final ActionListener buttonListenerNext = e -> {};
    private final ActionListener buttonListenerPrevious = e -> {};
    private final ActionListener buttonListenerShuffle = e -> {};
    private final ActionListener buttonListenerLoop = e -> {};
    private final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
        }

        @Override
        public void mousePressed(MouseEvent e) {
        }

        @Override
        public void mouseDragged(MouseEvent e) {
        }
    };

    public Player() {
        EventQueue.invokeLater(() -> window = new PlayerWindow(
                "Reprodutor",
                listaString,
                buttonListenerPlayNow,
                buttonListenerRemove,
                buttonListenerAddSong,
                buttonListenerShuffle,
                buttonListenerPrevious,
                buttonListenerPlayPause,
                buttonListenerStop,
                buttonListenerNext,
                buttonListenerLoop,
                scrubberMouseInputAdapter)
        );
    }

    //<editor-fold desc="Essential">

    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {
        // TODO Is this thread safe?
        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return false;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
        }
        return true;
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO Is this thread safe?
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        currentFrame++;
        return true;
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException Generic Bitstream exception.
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO Is this thread safe?
        if (newFrame > currentFrame) {
            int framesToSkip = newFrame - currentFrame;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
        }
    }
    //</editor-fold>
}
