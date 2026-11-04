package mx.buap.veterinaria.consultorio;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Esqueleto de la pantalla de consultorio (prototipo aprobado en la semana 3).
 * En la iteración 2 (semana 5) se conectan dueños, mascotas, consultas y el envío de la precuenta.
 */
public class ConsultorioApp extends Application {

    static final String SERVIDOR = System.getProperty("servidor", "http://localhost:8080");
    private static final String ESTILO = ConsultorioApp.class.getResource("estilo.css").toExternalForm();

    private final Label estadoServidor = new Label("Verificando servidor…");

    @Override
    public void start(Stage ventana) {
        // ---------- Barra superior ----------
        Label logo = new Label("VET");
        logo.getStyleClass().add("logo");
        Label marca = new Label("Sistema Veterinaria");
        marca.getStyleClass().add("marca");
        Label subtitulo = new Label("Consultorio  ·  Médico veterinario");
        subtitulo.getStyleClass().add("subtitulo");
        VBox identidad = new VBox(1, marca, subtitulo);
        identidad.setAlignment(Pos.CENTER_LEFT);
        estadoServidor.getStyleClass().add("estado");
        HBox barraSuperior = new HBox(logo, identidad, espacio(), estadoServidor);
        barraSuperior.getStyleClass().add("barra-superior");

        // ---------- Columna izquierda: paciente ----------
        TextField buscarDuenio = new TextField();
        buscarDuenio.setPromptText("Buscar dueño por nombre o teléfono");
        buscarDuenio.getStyleClass().add("campo-busqueda");

        ListView<String> mascotas = new ListView<>();
        mascotas.setPlaceholder(placeholder("Sin mascotas", "Busque primero al dueño"));
        mascotas.setPrefHeight(150);

        ListView<String> historial = new ListView<>();
        historial.setPlaceholder(placeholder("Sin historial", "Seleccione una mascota"));
        VBox.setVgrow(historial, Priority.ALWAYS);

        VBox paciente = new VBox(10,
                tituloTarjeta("Paciente"),
                etiquetaSeccion("DUEÑO"), buscarDuenio,
                etiquetaSeccion("MASCOTAS"), mascotas,
                etiquetaSeccion("HISTORIAL CLÍNICO"), historial);
        paciente.getStyleClass().add("tarjeta");
        paciente.setPrefWidth(320);
        paciente.setMinWidth(280);

        // ---------- Columna derecha: consulta ----------
        TextField motivo = new TextField();
        motivo.setPromptText("Ej. revisión anual, vómito, cojera…");

        TextField peso = new TextField();
        peso.setPromptText("0.0");
        TextField temperatura = new TextField();
        temperatura.setPromptText("38.5");
        HBox signos = new HBox(14,
                campo("PESO (KG)", peso),
                campo("TEMPERATURA (°C)", temperatura));
        signos.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));

        TextArea diagnostico = new TextArea();
        diagnostico.setPromptText("Hallazgos, diagnóstico y tratamiento indicado");
        diagnostico.setWrapText(true);
        diagnostico.setPrefRowCount(6);
        VBox.setVgrow(diagnostico, Priority.ALWAYS);

        VBox consulta = new VBox(10,
                tituloTarjeta("Consulta"),
                campo("MOTIVO DE LA CONSULTA", motivo),
                signos,
                etiquetaSeccion("DIAGNÓSTICO Y TRATAMIENTO"), diagnostico);
        consulta.getStyleClass().add("tarjeta");
        VBox.setVgrow(consulta, Priority.ALWAYS);

        // ---------- Precuenta ----------
        ListView<String> precuenta = new ListView<>();
        precuenta.setPlaceholder(placeholder("Precuenta vacía",
                "Los servicios y medicamentos aplicados aparecerán aquí"));
        precuenta.setPrefHeight(120);
        Label totalPrecuenta = new Label("$0.00");
        totalPrecuenta.getStyleClass().add("total-grande");
        totalPrecuenta.setStyle("-fx-font-size: 24px;");
        Button enviar = new Button("Enviar a caja   F10");
        enviar.getStyleClass().add("boton-primario");
        enviar.setDisable(true);
        Label pendiente = new Label("Disponible en la iteración 2");
        pendiente.getStyleClass().add("texto-suave");
        Tooltip.install(pendiente, new Tooltip("El envío de la precuenta a caja se conecta en la semana 5"));
        VBox totalCaja = new VBox(2, etiquetaSeccion("TOTAL"), totalPrecuenta);
        HBox pie = new HBox(14, totalCaja, espacio(), pendiente, enviar);
        pie.setAlignment(Pos.CENTER_LEFT);

        VBox tarjetaPrecuenta = new VBox(10, tituloTarjeta("Precuenta"), precuenta, pie);
        tarjetaPrecuenta.getStyleClass().add("tarjeta");

        VBox derecha = new VBox(16, consulta, tarjetaPrecuenta);
        HBox cuerpo = new HBox(16, paciente, derecha);
        HBox.setHgrow(derecha, Priority.ALWAYS);
        cuerpo.setPadding(new Insets(18, 22, 18, 22));

        BorderPane raiz = new BorderPane(cuerpo, barraSuperior, null, null, null);
        Scene escena = new Scene(raiz, 1100, 720);
        escena.getStylesheets().add(ESTILO);
        ventana.setTitle("Sistema Veterinaria · Consultorio");
        ventana.setMinWidth(900);
        ventana.setMinHeight(600);
        ventana.setScene(escena);
        ventana.show();
        buscarDuenio.requestFocus();
        verificarServidor();
    }

    private void verificarServidor() {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(SERVIDOR + "/api/salud")).GET().build();
        cliente.sendAsync(peticion, HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> Platform.runLater(() -> {
                    if (r.statusCode() == 200) {
                        ponerEstado("●  Servidor conectado", "estado-ok");
                    } else {
                        ponerEstado("●  Servidor: error " + r.statusCode(), "estado-error");
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> ponerEstado("●  Sin conexión al servidor", "estado-error"));
                    return null;
                });
    }

    private void ponerEstado(String texto, String clase) {
        estadoServidor.setText(texto);
        estadoServidor.getStyleClass().removeAll("estado-ok", "estado-local", "estado-error");
        estadoServidor.getStyleClass().add(clase);
        estadoServidor.setTooltip(new Tooltip("Servidor: " + SERVIDOR));
    }

    // ---------- Pequeñas piezas de interfaz ----------

    private static VBox campo(String etiqueta, Node control) {
        return new VBox(6, etiquetaSeccion(etiqueta), control);
    }

    private static Label tituloTarjeta(String texto) {
        Label titulo = new Label(texto);
        titulo.getStyleClass().add("titulo-tarjeta");
        return titulo;
    }

    private static Label etiquetaSeccion(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("etiqueta-seccion");
        return etiqueta;
    }

    private static Node placeholder(String titulo, String detalle) {
        Label principal = new Label(titulo);
        principal.getStyleClass().add("placeholder-titulo");
        Label secundario = new Label(detalle);
        secundario.getStyleClass().add("placeholder-texto");
        secundario.setWrapText(true);
        VBox caja = new VBox(4, principal, secundario);
        caja.setAlignment(Pos.CENTER);
        caja.setPadding(new Insets(10));
        return caja;
    }

    private static Region espacio() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
