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
import java.util.Collections;
import java.util.List;

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
    //Index da musica atual
    private int index;
    //Variavel para saber se havera mudanca na variavel index e qual mudanca sera
    private int indexChange = 1;
    //Frame sobre o qual vamos pular
    private int frameToSkip = -1;
    //Variável que contém o estado do botão de 'Loop'
    private boolean loop = false;
    //Variável que contém o estado do botão de 'Shuffle'
    private boolean shuffle = false;
    //Array reserva com as informacoes de cada musica que serao mostradas na tela ao tocar a musica
    private String[][] listaStringReserva;
    //Array reserva que vai guardar as musicas
    private Song[] listaSongReserva;
    //Array com os indexs aleatórios
    private Integer[] listaIndex;


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
        window.setEnabledLoopButton(true);
        window.setEnabledStopButton(Boolean.TRUE);

        window.setEnabledPreviousButton(indexNumber != 0);

        window.setEnabledNextButton(indexNumber != lista.length - 1);
        //window.setEnabledScrubber(true);
        window.setEnabledShuffleButton(false);
        window.setEnabledScrubber(true);
        currentFrame = 0;
    }


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
                while (index < listaString.length && !thr.isCancelled()) {
                    //Desenhando na tela as informacoes armazenadas em listaString sobre a musica
                    window.setPlayingSongInfo(listaSong[index].getTitle(), listaSong[index].getAlbum(), listaSong[index].getArtist());
                    //Resetando o estado do playPause
                    playPauseState = 1;

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
                        device.open(decoder = new Decoder());
                    } catch (JavaLayerException ex) {
                        continue;
                    }

                    try {
                        bitstream = new Bitstream(listaSong[index].getBufferedInputStream());
                    } catch (FileNotFoundException ex) {
                        continue;
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

                        //Botão Shuffle habilitado com duas ou mais músicas
                        if (listaString.length >= 2){
                            window.setEnabledShuffleButton(true);
                        }
                        if (shuffle){
                            index = 0;
                            window.setEnabledPreviousButton(false);
                            shuffle = false;
                        }

                        //Caso estejamos tocando a ultima musica da lista, vamos ativar o botão de 'Next'
                        window.setEnabledNextButton(index != listaSong.length - 1);

                        //Se o frame que queremos pular mudou de seu valor inicial, significa que vamos alterar o curso da musica
                        if(frameToSkip != -1) {
                            skipToFrame(frameToSkip);
                            //resetando o estado do frameToSkip
                            frameToSkip = -1;
                        }
                    }

                    //Se o botão de Previous foi pressionado
                    if (indexChange == 0) {
                        //decrementamos index, voltando uma musica
                        index--;
                        //resetando estado da variável booleana
                        indexChange = 1;
                    }
                    //Caso precise ir para a proxima musica
                    else if (indexChange == 1){
                        //Incrementando index para tocar a proxima musica
                        index++;
                    }
                    //Caso tenhamos removido a musica que esta tocando atualmente
                    else {
                        indexChange = 1;
                    }

                    if(loop && index == listaSong.length) {
                        index = 0;
                    }
                    //Configurando os botoes ("window reset")
                    else if (index == listaSong.length){
                        end_song();
                    }
                }
                //Configurando os botoes ("window reset")
                end_song();
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
            //Mantendo o index anterior inalterado
            indexChange = 2;
            //Saindo do loop que esta tocando a musica atual
            currentFrame = listaSong[index].getNumFrames();
            //Reconfigurando os botões
            end_song();
        }
        //Caso a musica a ser removida esteja antes da musica que esta sendo tocada
        else if(index > indexRemovido) {
            //Decrementamos o index para poder continuar tocando a mesma musica
            index--;

            //Reconfigurando os botoes Previous e Next caso necessario
            //Caso estejamos tocando a primeira musica da lista, vamos ativar o botão 'Previous'
            window.setEnabledPreviousButton(index != 0);

            //Caso estejamos tocando a ultima musica da lista, vamos ativar o botão 'Next'
            window.setEnabledNextButton(index != listaSong.length - 2);
        }

        //Removendo as informacoes da musica escolhida da listaString e da tela
        listaString = removeElement(listaString, indexRemovido);
        this.window.setQueueList(listaString);

        //Removendo os dados da musica escolhida da listaSong
        listaSong = removeElementSong(listaSong, indexRemovido);

        //Caso o modo Shuffle esteja ativado, a remoção também precisa ocorrer nas listas reservas
        if (shuffle) {
            listaStringReserva = removeElement(listaStringReserva, listaIndex[indexRemovido]);
            listaSongReserva = removeElementSong(listaSongReserva, listaIndex[indexRemovido]);
        }
    };


    /**
     * Funcao principal do botao 'Add Song'
     */
    private final ActionListener buttonListenerAddSong = e -> {
        //Criando uma variavel e armazenando a nova musica nela
        Song novo;
        try {
            novo = this.window.openFileChooser();
        } catch (IOException | BitstreamException | UnsupportedTagException | InvalidDataException ex) {
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

            //Caso o modo Shuffle esteja ativado, a adição também precisa ocorrer nas listas reservas
            if (shuffle) {
                listaStringReserva = Arrays.copyOf(listaStringReserva, N + 1);
                listaStringReserva[N] = novo.getDisplayInfo();
                listaSongReserva = Arrays.copyOf(listaSongReserva, N2 + 1);
                listaSongReserva[N2] = novo;
            }
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


    /**
     * Função principal do botao 'Next'
     */
    private final ActionListener buttonListenerNext = e -> {
        //Mudando currentFrame para sair do loop da musica atual e passarmos para a proxima
        currentFrame = listaSong[index].getNumFrames();
    };


    /**
     * Função principal do botao 'Previous'
     */
    private final ActionListener buttonListenerPrevious = e -> {
        indexChange = 0;
        currentFrame = listaSong[index].getNumFrames();
    };


    /**
     * Função principal do botao 'Shuffle'
     */
    private final ActionListener buttonListenerShuffle = e -> {
        if(listaSongReserva == null) {
            shuffle = true;
        }

        //Se o botão de shuffle foi ativado
        if (shuffle){
            //Copiando valores das listas com informações para as listas reservas
            listaStringReserva = listaString.clone();
            listaSongReserva = listaSong.clone();

            listaString[0] = listaString[index];
            listaSong[0] = listaSong[index];

            //Criando o array de indexs
            listaIndex = new Integer[listaSong.length];

            //Colocando os indexs de maneira ordenada, de 0 a N
            for (int i = 0; i < listaSong.length; i++) {
                listaIndex[i] = i;
            }

            //Criando um ArrayList com o Array de indexs para usar o método de shuffle
            List<Integer> intList = Arrays.asList(listaIndex);
            //Ordenando aleatóriamente o ArrayList
            Collections.shuffle(intList);
            //Retornando os valores do ArrayList para o Array de indexs
            intList.toArray(listaIndex);
            int in = 0;

            //Sincronizando os arrays de informação com os novos indexs aleatórios
            for (int i = 1; i < listaSong.length; i++) {
                if (listaStringReserva[listaIndex[in]] == listaString[0]) {
                    in++;
                }
                listaString[i] = listaStringReserva[listaIndex[in]];
                listaSong[i] = listaSongReserva[listaIndex[in]];
                in++;
            }

            //Atualizando a janela com a nova ordem das músicas
            window.setQueueList(listaString);

            //System.out.println(Arrays.toString(listaIndex));

        }

        //Se o botão de shuffle foi desativado
        else {
            //Voltando as listas com informações ao estado original contido nas listas reservas (caso tenha sido feita alguma
            // adição ou remoção, também já foi feita nos arrays reservas)
            listaString = listaStringReserva.clone();
            listaSong = listaSongReserva.clone();

            //Atualizando a tela com a ordem antiga das músicas + as alterações, se houve alguma
            window.setQueueList(listaString);

            listaSongReserva = null;
            listaStringReserva = null;
        }

    };


    /**
     * Função principal do botao 'Loop'
     */
    private final ActionListener buttonListenerLoop = e -> loop = !loop;


    /**
     * Função principal das interacoes com o 'Scrubber'
     */
    private final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
        }

        @Override
        public void mousePressed(MouseEvent e) {
            //Armazendo o frame para qual vamos pular no curso da musica
            frameToSkip = (int) (window.getScrubberValue()/listaSong[index].getMsPerFrame());
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            //Armazendo o frame para qual vamos pular no curso da musica
            frameToSkip = (int) (window.getScrubberValue()/listaSong[index].getMsPerFrame());
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
     *
     */
    private void playNextFrame() throws JavaLayerException {
        // TODO Is this thread safe?
        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
        }
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
        //Caso queiramos pular para um frame anterior da musica
        if(newFrame < currentFrame) {
            //Fechando bitstream e device
            try {
                bitstream.close();
            } catch (BitstreamException ex) {
                throw new RuntimeException(ex);
            }

            //Fechando o device antigo
            device.close();

            //Armazenando novos Audio Device, Decoder e Bitstream nessas variaveis
            try {
                device = FactoryRegistry.systemRegistry().createAudioDevice();
                device.open(decoder = new Decoder());
            } catch (JavaLayerException ignored) {

            }

            try {
                bitstream = new Bitstream(listaSong[index].getBufferedInputStream());
            } catch (FileNotFoundException ignored) {
            }

            //Resetando o valor de currentFrame
            currentFrame = 0;
        }
        int framesToSkip = newFrame - currentFrame;
        boolean condition = true;
        while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
    }
    //</editor-fold>
}