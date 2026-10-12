import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalizadorTexto {

    public static void main(String[] args) {
        String rutaArchivo = "quijote.txt";
        String contenidoOriginal = "";

        try {
            contenidoOriginal = new String(Files.readAllBytes(Paths.get(rutaArchivo)), "UTF-8");
        } catch (IOException e) {
            System.out.println("Error al leer el archivo: " + e.getMessage());
            return;
        }

        // Crear la versión ampliada (repetir el contenido 100 veces)
        StringBuilder sbAmpliado = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sbAmpliado.append(contenidoOriginal).append("\n");
        }
        String contenidoAmpliado = sbAmpliado.toString();

        String palabraBuscada = "Quijote";
        String secuenciaBuscada = "ción";

        System.out.println("=== PRUEBAS DE RENDIMIENTO Y PROCESAMIENTO ===");

        // ==========================================
        // PROCESAMIENTO SECUENCIAL (1 Hilo)
        // ==========================================
        ejecutarSecuencial("Original", contenidoOriginal, palabraBuscada, secuenciaBuscada);
        ejecutarSecuencial("Ampliado", contenidoAmpliado, palabraBuscada, secuenciaBuscada);

        // ==========================================
        // PROCESAMIENTO PARALELO (2 Hilos)
        // ==========================================
        ejecutarParalelo("Original", contenidoOriginal, palabraBuscada, secuenciaBuscada, 2);
        ejecutarParalelo("Ampliado", contenidoAmpliado, palabraBuscada, secuenciaBuscada, 2);

        // ==========================================
        // PROCESAMIENTO PARALELO (4 Hilos)
        // ==========================================
        ejecutarParalelo("Original", contenidoOriginal, palabraBuscada, secuenciaBuscada, 4);
        ejecutarParalelo("Ampliado", contenidoAmpliado, palabraBuscada, secuenciaBuscada, 4);
    }

    // --- IMPLEMENTACIÓN SECUENCIAL ---
    public static void ejecutarSecuencial(String tipoDataset, String texto, String palabra, String secuencia) {
        long sumaTiempos = 0;
        int repeticiones = 5;

        long pPalabras = 0, pOcurrencias = 0, pSecuencia = 0;
        Pattern patronPalabra = Pattern.compile("\\b" + Pattern.quote(palabra) + "\\b", Pattern.CASE_INSENSITIVE);
        Pattern patronSecuencia = Pattern.compile(Pattern.quote(secuencia), Pattern.CASE_INSENSITIVE);
        Pattern patronTokens = Pattern.compile("\\w+");

        for (int i = 0; i < repeticiones; i++) {
            long inicio = System.nanoTime();

            pPalabras = 0;
            pOcurrencias = 0;
            pSecuencia = 0;

            Matcher mTokens = patronTokens.matcher(texto);
            while (mTokens.find()) {
                pPalabras++;
            }

            Matcher mPalabra = patronPalabra.matcher(texto);
            while (mPalabra.find()) {
                pOcurrencias++;
            }

            Matcher mSecuencia = patronSecuencia.matcher(texto);
            while (mSecuencia.find()) {
                pSecuencia++;
            }

            long fin = System.nanoTime();
            sumaTiempos += (fin - inicio);
        }

        double promedioMs = (sumaTiempos / (double) repeticiones) / 1_000_000.0;
        System.out.printf(
                "[Secuencial - %s] Tiempo promedio: %.4f ms | Palabras: %d | Ocurrencias ('%s'): %d | Secuencia ('%s'): %d%n",
                tipoDataset, promedioMs, pPalabras, palabra, pOcurrencias, secuencia, pSecuencia);
    }

    // --- IMPLEMENTACIÓN PARALELA CON THREADS ---
    public static void ejecutarParalelo(String tipoDataset, String texto, String palabra, String secuencia,
            int numHilos) {
        long sumaTiempos = 0;
        int repeticiones = 5;

        long totalPalabras = 0;
        long totalOcurrencias = 0;
        long totalSecuencia = 0;

        int longitudTexto = texto.length();

        for (int r = 0; r < repeticiones; r++) {
            long inicio = System.nanoTime();

            Thread[] hilos = new Thread[numHilos];
            AnalizadorWorker[] workers = new AnalizadorWorker[numHilos];
            int tamBloque = longitudTexto / numHilos;

            for (int i = 0; i < numHilos; i++) {
                int inicioIdx = i * tamBloque;
                int finIdx = (i == numHilos - 1) ? longitudTexto : (inicioIdx + tamBloque);

                // Ajustar los límites para no cortar palabras a la mitad
                if (i > 0) {
                    while (inicioIdx < longitudTexto && Character.isLetterOrDigit(texto.charAt(inicioIdx))) {
                        inicioIdx++;
                    }
                }
                if (i < numHilos - 1) {
                    while (finIdx < longitudTexto && Character.isLetterOrDigit(texto.charAt(finIdx))) {
                        finIdx++;
                    }
                }

                workers[i] = new AnalizadorWorker(texto, inicioIdx, finIdx, palabra, secuencia);
                hilos[i] = new Thread(workers[i]);
                hilos[i].start();
            }

            for (int i = 0; i < numHilos; i++) {
                try {
                    hilos[i].join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            totalPalabras = 0;
            totalOcurrencias = 0;
            totalSecuencia = 0;

            for (int i = 0; i < numHilos; i++) {
                totalPalabras += workers[i].getParcialPalabras();
                totalOcurrencias += workers[i].getParcialOcurrencias();
                totalSecuencia += workers[i].getParcialSecuencia();
            }

            long fin = System.nanoTime();
            sumaTiempos += (fin - inicio);
        }

        double promedioMs = (sumaTiempos / (double) repeticiones) / 1_000_000.0;
        System.out.printf(
                "[Paralelo %d Hilos - %s] Tiempo promedio: %.4f ms | Palabras: %d | Ocurrencias: %d | Secuencia: %d%n",
                numHilos, tipoDataset, promedioMs, totalPalabras, totalOcurrencias, totalSecuencia);
    }

    // Clase interna que procesa porciones de texto de forma segura con hilos
    public static class AnalizadorWorker implements Runnable {
        private String textoCompleto;
        private int inicioIdx;
        private int finIdx;
        private String palabraBusca;
        private String secuenciaBusca;

        private long parcialPalabras = 0;
        private long parcialOcurrencias = 0;
        private long parcialSecuencia = 0;

        public AnalizadorWorker(String textoCompleto, int inicioIdx, int finIdx, String palabraBusca,
                String secuenciaBusca) {
            this.textoCompleto = textoCompleto;
            this.inicioIdx = inicioIdx;
            this.finIdx = finIdx;
            this.palabraBusca = palabraBusca;
            this.secuenciaBusca = secuenciaBusca;
        }

        @Override
        public void run() {
            if (inicioIdx >= finIdx)
                return;
            String subTexto = textoCompleto.substring(inicioIdx, finIdx);

            Pattern patronTokens = Pattern.compile("\\w+");
            Matcher mTokens = patronTokens.matcher(subTexto);
            while (mTokens.find()) {
                parcialPalabras++;
            }

            Pattern patronPalabra = Pattern.compile("\\b" + Pattern.quote(palabraBusca) + "\\b",
                    Pattern.CASE_INSENSITIVE);
            Matcher mPalabra = patronPalabra.matcher(subTexto);
            while (mPalabra.find()) {
                parcialOcurrencias++;
            }

            Pattern patronSecuencia = Pattern.compile(Pattern.quote(secuenciaBusca), Pattern.CASE_INSENSITIVE);
            Matcher mSecuencia = patronSecuencia.matcher(subTexto);
            while (mSecuencia.find()) {
                parcialSecuencia++;
            }
        }

        public long getParcialPalabras() {
            return parcialPalabras;
        }

        public long getParcialOcurrencias() {
            return parcialOcurrencias;
        }

        public long getParcialSecuencia() {
            return parcialSecuencia;
        }
    }
}