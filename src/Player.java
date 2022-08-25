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
    private String[][] listaString = new String[1][];
    private Song[] listaSong = new Song[1];
    int stopPlayNow = 0;

    private int currentFrame = 0;

    private final ActionListener buttonListenerPlayNow = e -> {
        stopPlayNow = 1;
        new SwingWorker() {
        @Override
        protected Object doInBackground() throws Exception {
            window.setPlayingSongInfo(listaSong[0].getTitle(), listaSong[0].getAlbum(), listaSong[0].getArtist());
            currentFrame = 0;
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
                bitstream = new Bitstream(listaSong[0].getBufferedInputStream());
            } catch (FileNotFoundException ex) {}

            stopPlayNow = 0;
            while (true) {
                try {
                    if(stopPlayNow == 1){
                        break;
                    }
                    playNextFrame();
                } catch (JavaLayerException ex) {}
            }
            return null;
        }
    }.execute();
    };

    private final ActionListener buttonListenerRemove = e -> {};
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

        this.window.setQueueList(listaString, novo.getDisplayInfo());
        listaSong[0] = novo;
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
