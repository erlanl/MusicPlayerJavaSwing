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

    //Lista com as informacoes de cada musica que serao mostradas na tela ao tocar a musica
    private String[][] listaString = new String[0][];
    //Lista que vai guardar as musicas
    private Song[] listaSong = new Song[0];
    //Lista que guarda o frame atual da musica
    private int currentFrame = 0;
    //Thread que vai rodar o loop que ira tocar a musica escolhida
    private SwingWorker thr;
    //Variavel para dizer se a musica esta pausada
    //1 = play e 0 = pause
    private int playPauseState = 1;
    private int index;
    private boolean boolPrevious = false;
    private boolean skipFrame = false;
    private int frameToSkip = -1;

    /**
     * Remove a musica que sera excluida da lista de Strings
     * @param arr com a lista de strings que guarda as informacoes das musicas na tela
     * @param indexRemoved da musica que sera excluida
     * @return lista sem as informacoes da musica excluida
     */
    public static String[][] removeElement( String [][] arr, int indexRemoved ){
        //Criando nova lista com 1 tamanho a menos
        String[][] arrDestination = new String[arr.length - 1][];
        //Armazenando o indice da musica excluida na ListaString
        int remainingElements = arr.length - ( indexRemoved + 1 );
        //Copiando os elementos de ListaString que estao antes do indice da musica excluida para a lista nova
        System.arraycopy(arr, 0, arrDestination, 0, indexRemoved);
        //Copiando os elementos de ListaString que estao depois do indice para a lista nova
        System.arraycopy(arr, indexRemoved + 1, arrDestination, indexRemoved, remainingElements);
        //Retornando a lista nova
        return arrDestination;
    }

    /**
     * Remove a musica que sera excluida da lista de Songs
     * Funciona da mesma forma que a funcao removeElement acima, porem para a classe Song e a variavel listaSong
     * @param arr com a lista de Song que guarda os dados necessarios para tocar as musicas adicionadas
     * @param indexRemoved da musica que sera excluida
     * @return lista sem os dados da musica excluida
     */
    public static Song[] removeElementSong( Song [] arr, int indexRemoved ){
        Song[] arrDestination = new Song[arr.length - 1];
        int remainingElements = arr.length - ( indexRemoved + 1 );
        System.arraycopy(arr, 0, arrDestination, 0, indexRemoved);
        System.arraycopy(arr, indexRemoved + 1, arrDestination, indexRemoved, remainingElements);
        return arrDestination;
    }

    /**
     * Configura os botoes na tela quando apertamos 'play now'
     */
    public void start_window(int indexNumber, Song [] lista) {
        window.setPlayPauseButtonIcon(playPauseState);
        window.setEnabledPlayPauseButton(true);
        window.setEnabledLoopButton(false);
        window.setEnabledStopButton(Boolean.TRUE);

        if (indexNumber == 0) {
            window.setEnabledPreviousButton(false);
        }
        else {
            window.setEnabledPreviousButton(true);
        }

        if(indexNumber == lista.length - 1) {
            window.setEnabledNextButton(false);
        }
        else {
            window.setEnabledNextButton(true);
        }
        //window.setEnabledScrubber(true);
        window.setEnabledShuffleButton(false);
        window.setEnabledScrubber(true);
        currentFrame = 0;
    };

    /**
     * Configura os botoes na tela quando terminamos a musica ou apertamos 'stop'
     */
    public void end_song() {
        window.setPlayPauseButtonIcon(playPauseState);
        window.setEnabledPlayPauseButton(false);
        window.setEnabledLoopButton(false);
        window.setEnabledStopButton(Boolean.FALSE);
        window.setEnabledPreviousButton(false);
        window.setEnabledNextButton(false);
        window.setEnabledShuffleButton(false);
        window.setEnabledScrubber(false);
        window.resetMiniPlayer();
    }

    /**
     * Funcao principal do botao 'play now'
     */
    private final ActionListener buttonListenerPlayNow = e -> {
        //Caso em algum momento uma thread tenha sido criada e armazenada em thr
        if(thr != null){
            //Terminando a thead e armazenando 0 em currentFrame
            thr.cancel(true);
            currentFrame = 0;
        }
        //Iniciando uma nova thread para tocar a nova musica
        thr = new SwingWorker() {
            @Override
            protected Object doInBackground() throws Exception {
                //Pegando a posicao(indice) da musica na listaString, que eh a mesma posicao na listaSong
                index = window.getIndex(listaString);

                //Loop para tocar todas as musicas da lista
                while (index != listaString.length && !thr.isCancelled()) {
                    //Desenhando na tela as informacoes armazenadas em listaString sobre a musica
                    window.setPlayingSongInfo(listaSong[index].getTitle(), listaSong[index].getAlbum(), listaSong[index].getArtist());

                    //Caso o bittstream esteja armazenando algo
                    if (bitstream != null) {
                        //Fechando o bitstream antigo
                        try {
                            bitstream.close();
                        } catch (BitstreamException ex) {
                            throw new RuntimeException(ex);
                        }
                        //Fechando o device antigo
                        device.close();
                    }

                    //Armazenando novos Audio Device, Decoder e Bitstream nessas variaveis
                    try {
                        device = FactoryRegistry.systemRegistry().createAudioDevice();
                    } catch (JavaLayerException ex) {
                    }

                    try {
                        device.open(decoder = new Decoder());
                    } catch (JavaLayerException ex) {
                    }

                    try {
                        bitstream = new Bitstream(listaSong[index].getBufferedInputStream());
                    } catch (FileNotFoundException ex) {
                    }

                    //Configurando os botaos
                    start_window(index, listaSong);

                    //Loop para tocar a musica, ira terminar caso a musica termine ou caso tentem terminar a thread e nao consigam
                    while (currentFrame < listaSong[index].getNumFrames() && !thr.isCancelled()) {
                        //Caso a musica esteja no estado de play
                        if (playPauseState == 1) {
                            //Tocando o proximo frame
                            playNextFrame();
                            //Calculando o tempo em que a musica esta e pondo na tela
                            window.setTime((int) (currentFrame * listaSong[index].getMsPerFrame()), (int) (listaSong[index].getNumFrames() * listaSong[index].getMsPerFrame()));
                            currentFrame++;
                        }
                        if(frameToSkip != -1) {
                            skipToFrame(frameToSkip);
                            frameToSkip = -1;
                        }
                    }

                    if (boolPrevious) {
                        index--;
                        boolPrevious = false;
                    }
                    else {
                        //Incrementando index para tocar a proxima musica
                        index++;
                    }
                    //Configurando os botoes
                    end_song();
                }
                return null;
            }
        };
        //Iniciando a execucao da thread criada
        thr.execute();
    };

    /**
     * Funcao principal do botao 'Remove'
     */
    private final ActionListener buttonListenerRemove = e -> {
        //Armazendando o index da musica a ser removida
        int indexRemovido = window.getIndex(listaString);

        //Caso a musica a ser removida esteja sendo tocada
        if(index == indexRemovido) {
            //A linha abaixo eh para que o comando !thr.isCancelled() na linha 158 retorne false e o loop que toca
            // a musica seja interrompido e a proxima sera tocada
            currentFrame = listaSong[index].getNumFrames();
        }

        //Removendo as informacoes da musica escolhida da listaString e da tela
        listaString = removeElement(listaString, indexRemovido);
        this.window.setQueueList(listaString);

        //Removendo os dados da musica escolhida da listaSong
        listaSong = removeElementSong(listaSong, indexRemovido);

    };

    /**
     * Funcao principal do botao 'Add Song'
     */
    private final ActionListener buttonListenerAddSong = e -> {
        //Criando uma variavel e armazenando a nova musica nela
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

        //Caso openFileChooser retorne uma musica
        if (novo != null) {
            //Armazenando o indice da posicao da nova musica na listaString e listaSong
            int N = listaString.length;
            //Aumentando o tamanho da lista atual em 1
            listaString = Arrays.copyOf(listaString, N + 1);
            //Acrescentando a musica nova no final da lista aumentada e na tela
            listaString[N] = novo.getDisplayInfo();
            this.window.setQueueList(listaString);

            //Mesma logica mostrada acima, porem sem modificar a tela
            int N2 = listaSong.length;
            listaSong = Arrays.copyOf(listaSong, N2 + 1);
            listaSong[N2] = novo;
        }
    };

    /**
     * Funcao principal do botao 'play/pause'
     */
    private final ActionListener buttonListenerPlayPause = e -> {
        //Caso esse botao tenha sido presionado, trocamos o estado
        switch (playPauseState){
            case 0 -> playPauseState = 1;
            case 1 -> playPauseState = 0;
        }
        //Atualizando a imagem do botao play/pause
        window.setPlayPauseButtonIcon(playPauseState);
    };

    /**
     * Funcao principal do botao 'stop'
     */
    private final ActionListener buttonListenerStop = e -> {
        //A linha abaixo eh para que o comando !thr.isCancelled() na linha 158 retorne false e o loop que toca
        // a musica seja interrompido, terminando a thread
        thr.cancel(true);
    };
    private final ActionListener buttonListenerNext = e -> {
        currentFrame = listaSong[index].getNumFrames();
    };
    private final ActionListener buttonListenerPrevious = e -> {
        boolPrevious = true;
        currentFrame = listaSong[index].getNumFrames();
    };
    private final ActionListener buttonListenerShuffle = e -> {};
    private final ActionListener buttonListenerLoop = e -> {};
    private final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
            frameToSkip = (int) (window.getScrubberValue()/listaSong[index].getMsPerFrame());
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

        /*else if(newFrame < currentFrame) {
        **
        }*/
    }
    //</editor-fold>
}
