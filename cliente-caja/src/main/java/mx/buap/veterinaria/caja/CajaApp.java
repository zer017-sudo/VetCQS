package mx.buap.veterinaria.caja;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.converter.BigDecimalStringConverter;

public class CajaApp extends Application {

    static final String SERVIDOR = System.getProperty("servidor", "http://localhost:8080");
    private static final String ESTILO = CajaApp.class.getResource("estilo.css").toExternalForm();
    private static final NumberFormat MONEDA = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-MX"));

    static {
        MONEDA.setMinimumFractionDigits(2);
        MONEDA.setMaximumFractionDigits(2);
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final ObservableList<Renglon> carrito = FXCollections.observableArrayList();
    private final Label estadoServidor = new Label("Verificando servidor…");
    private final Label total = new Label("$0.00");
    private final Label resumenCarrito = new Label("Sin productos");
    private final Label contadorRenglones = new Label("0");
    private final TableView<Renglon> tabla = new TableView<>(carrito);
    private Stage ventanaPrincipal;
    private boolean servidorDisponible;
    private static final List<ProductoTemporal> PRODUCTOS_DE_PRUEBA = List.of(
            new ProductoTemporal(101, "7501000000011", "Croquetas adulto 2 kg", new BigDecimal("389.00"), new BigDecimal("2")),
            new ProductoTemporal(102, "7501000000028", "Desparasitante, tableta", new BigDecimal("45.00"), new BigDecimal("40")),
            new ProductoTemporal(103, "7501000000035", "Arena para gato 5 kg", new BigDecimal("159.00"), new BigDecimal("3")),
            new ProductoTemporal(104, "7501000000042", "Vacuna múltiple canina", new BigDecimal("280.00"), new BigDecimal("8")),
            new ProductoTemporal(105, null, "Consulta general", new BigDecimal("350.00"), BigDecimal.ZERO),
            new ProductoTemporal(106, null, "Aplicación de vacuna", new BigDecimal("120.00"), BigDecimal.ZERO));

    @Override
    public void start(Stage ventana) {
        ventanaPrincipal = ventana;

        // ---------- Barra superior ----------
        Label logo = new Label("VET");
        logo.getStyleClass().add("logo");
        Label marca = new Label("Sistema Veterinaria");
        marca.getStyleClass().add("marca");
        Label subtitulo = new Label("Punto de venta  ·  Caja");
        subtitulo.getStyleClass().add("subtitulo");
        VBox identidad = new VBox(1, marca, subtitulo);
        identidad.setAlignment(Pos.CENTER_LEFT);
        estadoServidor.getStyleClass().add("estado");
        HBox barraSuperior = new HBox(logo, identidad, espacio(), estadoServidor);
        barraSuperior.getStyleClass().add("barra-superior");

        // ---------- Captura de productos ----------
        TextField captura = new TextField();
        captura.setPromptText("Escanee el código de barras o escriba el nombre del producto");
        captura.getStyleClass().add("campo-busqueda");
        captura.setOnAction(e -> buscar(captura));
        HBox.setHgrow(captura, Priority.ALWAYS);
        Button agregar = new Button("Agregar");
        agregar.getStyleClass().add("boton-marca");
        agregar.setOnAction(e -> buscar(captura));
        HBox filaCaptura = new HBox(10, captura, agregar);
        filaCaptura.setAlignment(Pos.CENTER);

        // ---------- Tabla de la venta ----------
        TableColumn<Renglon, BigDecimal> cantidad = new TableColumn<>("CANT.");
        cantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        cantidad.setEditable(true);
        cantidad.setOnEditCommit(e -> {
            BigDecimal nuevaCantidad = e.getNewValue();
            if (nuevaCantidad != null && nuevaCantidad.compareTo(BigDecimal.ZERO) > 0) {
                e.getRowValue().setCantidad(nuevaCantidad);
                actualizarTotal();
            }
            tabla.refresh();
        });
        cantidad.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter() {
            @Override
            public String toString(BigDecimal valor) {
                return valor == null ? "" : valor.stripTrailingZeros().toPlainString();
            }
        }));
        cantidad.getStyleClass().add("col-centro");
        cantidad.setPrefWidth(80);
        cantidad.setMaxWidth(110);
        TableColumn<Renglon, String> nombre = new TableColumn<>("PRODUCTO");
        nombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        nombre.setPrefWidth(360);
        TableColumn<Renglon, BigDecimal> precio = new TableColumn<>("PRECIO UNIT.");
        precio.setCellValueFactory(new PropertyValueFactory<>("precio"));
        precio.setCellFactory(columna -> celdaMonetaria());
        precio.getStyleClass().add("col-dinero");
        precio.setPrefWidth(120);
        TableColumn<Renglon, BigDecimal> importe = new TableColumn<>("IMPORTE");
        importe.setCellValueFactory(new PropertyValueFactory<>("importe"));
        importe.setCellFactory(columna -> celdaMonetaria());
        importe.getStyleClass().add("col-importe");
        importe.setPrefWidth(130);
        tabla.getColumns().addAll(cantidad, nombre, precio, importe);
        tabla.setEditable(true);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(placeholder("La venta está vacía",
                "Escanee un producto o búsquelo por nombre para comenzar"));
        tabla.setTooltip(new Tooltip("Doble clic en la cantidad para modificarla · SUPR elimina el renglón"));

        Label tituloVenta = new Label("Venta actual");
        tituloVenta.getStyleClass().add("titulo-tarjeta");
        contadorRenglones.getStyleClass().add("contador");
        HBox encabezadoVenta = new HBox(8, tituloVenta, contadorRenglones);
        encabezadoVenta.setAlignment(Pos.CENTER_LEFT);
        VBox tarjetaVenta = new VBox(12, filaCaptura, encabezadoVenta, tabla);
        tarjetaVenta.getStyleClass().add("tarjeta");
        VBox.setVgrow(tabla, Priority.ALWAYS);

        // ---------- Panel del total ----------
        Label tituloTotal = new Label("TOTAL A COBRAR");
        tituloTotal.getStyleClass().add("etiqueta-seccion");
        total.getStyleClass().add("total-grande");
        resumenCarrito.getStyleClass().add("texto-suave");
        Button cobrar = new Button("Cobrar   F12");
        cobrar.getStyleClass().add("boton-primario");
        cobrar.setMaxWidth(Double.MAX_VALUE);
        cobrar.setOnAction(e -> cobrar());
        Button inventario = new Button("Revisar inventario   F4");
        inventario.getStyleClass().add("boton-secundario");
        inventario.setMaxWidth(Double.MAX_VALUE);
        inventario.setOnAction(e -> revisarInventario());
        Region empuje = new Region();
        VBox.setVgrow(empuje, Priority.ALWAYS);
        VBox panelTotal = new VBox(10, tituloTotal, total, resumenCarrito, separador(),
                empuje, cobrar, inventario);
        panelTotal.getStyleClass().add("tarjeta");
        panelTotal.setPrefWidth(280);
        panelTotal.setMinWidth(260);

        HBox cuerpo = new HBox(16, tarjetaVenta, panelTotal);
        HBox.setHgrow(tarjetaVenta, Priority.ALWAYS);
        cuerpo.setPadding(new Insets(18, 22, 6, 22));

        // ---------- Atajos ----------
        HBox atajos = new HBox(
                tecla("ENTER"), descripcionTecla("Buscar y agregar"),
                tecla("SUPR"), descripcionTecla("Eliminar renglón"),
                tecla("F4"), descripcionTecla("Inventario"),
                tecla("F12"), descripcionTecla("Cobrar"));
        atajos.getStyleClass().add("barra-atajos");

        BorderPane raiz = new BorderPane(cuerpo, barraSuperior, null, atajos, null);
        Scene escena = new Scene(raiz, 1100, 680);
        escena.getStylesheets().add(ESTILO);
        escena.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F12) {
                cobrar();
                e.consume();
            } else if (e.getCode() == KeyCode.F4) {
                revisarInventario();
                e.consume();
            }
        });
        escena.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                eliminarSeleccionado();
            }
        });

        ventana.setTitle("Sistema Veterinaria · Caja");
        ventana.setMinWidth(900);
        ventana.setMinHeight(560);
        ventana.setScene(escena);
        ventana.show();
        captura.requestFocus();
        verificarServidor();
    }

    private void buscar(TextField captura) {
        String texto = captura.getText().trim();
        if (texto.isEmpty()) return;
        if (!servidorDisponible) {
            buscarProductoDePrueba(texto, captura);
            return;
        }
        String ruta = texto.matches("\\d+") ? "/api/productos/codigo/" + texto : "/api/productos?q=" + encode(texto);
        request("GET", ruta, null).thenAccept(respuesta -> Platform.runLater(() -> {
            try {
                JsonNode nodo = json.readTree(respuesta.body());
                if (respuesta.statusCode() == 200) {
                    JsonNode producto = nodo.isArray() ? (nodo.size() == 0 ? null : nodo.get(0)) : nodo;
                    if (producto == null) throw new IllegalArgumentException("No se encontró el producto");
                    agregar(producto);
                    captura.clear();
                } else {
                    mostrarError("Producto no encontrado", "No se encontró un producto activo con «" + texto + "».");
                }
            } catch (Exception ex) {
                mostrarError(ex.getMessage());
            }
        })).exceptionally(ex -> {
            Platform.runLater(() -> mostrarError("Sin conexión",
                    "No se pudo consultar el servidor. Verifique la red e intente de nuevo."));
            return null;
        });
    }

    private void buscarProductoDePrueba(String texto, TextField captura) {
        String consulta = texto.toLowerCase(Locale.ROOT);
        ProductoTemporal producto = PRODUCTOS_DE_PRUEBA.stream()
                .filter(p -> texto.equals(p.codigo()) || p.nombre().toLowerCase(Locale.ROOT).contains(consulta))
                .findFirst()
                .orElse(null);
        if (producto == null) {
            mostrarError("Producto no encontrado", "No se encontró «" + texto + "» en los datos de prueba.");
            return;
        }
        agregar(productoJson(producto));
        captura.clear();
    }

    private JsonNode productoJson(ProductoTemporal producto) {
        return json.createObjectNode()
                .put("id", producto.id())
                .put("codigoBarras", producto.codigo())
                .put("nombre", producto.nombre())
                .put("precio", producto.precio())
                .put("stockActual", producto.stock());
    }

    private void agregar(JsonNode producto) {
        int id = producto.get("id").asInt();
        BigDecimal precio = producto.get("precio").decimalValue();
        Renglon renglon = carrito.stream().filter(r -> r.id == id).findFirst().orElse(null);
        if (renglon != null) {
            renglon.cantidad = renglon.cantidad.add(BigDecimal.ONE);
        } else {
            renglon = new Renglon(id, producto.get("nombre").asText(), precio);
            carrito.add(renglon);
        }
        actualizarTotal();
        tabla.refresh();
        tabla.getSelectionModel().select(renglon);
        tabla.scrollTo(renglon);
    }

    private void cobrar() {
        if (carrito.isEmpty()) {
            mostrarError("Venta vacía", "Agregue al menos un producto antes de cobrar.");
            return;
        }
        BigDecimal importe = total();

        // Lista de productos con importes alineados a la derecha
        GridPane productos = new GridPane();
        productos.setHgap(16);
        productos.setVgap(6);
        productos.getStyleClass().add("tarjeta");
        int fila = 0;
        for (Renglon renglon : carrito) {
            Label descripcion = new Label(renglon.getCantidad().stripTrailingZeros().toPlainString()
                    + " × " + renglon.getNombre());
            descripcion.getStyleClass().add("renglon-resumen");
            Label monto = new Label(dinero(renglon.getImporte()));
            monto.getStyleClass().add("renglon-resumen");
            GridPane.setHgrow(descripcion, Priority.ALWAYS);
            GridPane.setHalignment(monto, javafx.geometry.HPos.RIGHT);
            productos.addRow(fila++, descripcion, monto);
        }

        Label tituloTotal = new Label("TOTAL A PAGAR");
        tituloTotal.getStyleClass().add("etiqueta-seccion");
        Label totalDialogo = new Label(dinero(importe));
        totalDialogo.getStyleClass().add("total-grande");

        Label recibidoLabel = new Label("EFECTIVO RECIBIDO");
        recibidoLabel.getStyleClass().add("etiqueta-seccion");
        TextField recibido = new TextField(importe.setScale(2, RoundingMode.HALF_UP).toPlainString());
        recibido.setPromptText("0.00");
        recibido.getStyleClass().add("campo-grande");
        Label cambioLabel = new Label("CAMBIO");
        cambioLabel.getStyleClass().add("etiqueta-seccion");
        Label cambio = new Label(dinero(BigDecimal.ZERO));
        cambio.getStyleClass().add("cambio-grande");
        recibido.textProperty().addListener((obs, anterior, nuevo) -> {
            BigDecimal efectivo = leerDinero(nuevo);
            if (efectivo == null) {
                cambio.setText("—");
            } else if (efectivo.compareTo(importe) < 0) {
                cambio.setText("Faltan " + dinero(importe.subtract(efectivo)));
            } else {
                cambio.setText(dinero(efectivo.subtract(importe)));
            }
        });

        VBox columnaRecibido = new VBox(6, recibidoLabel, recibido);
        VBox columnaCambio = new VBox(6, cambioLabel, cambio);
        HBox.setHgrow(columnaRecibido, Priority.ALWAYS);
        HBox.setHgrow(columnaCambio, Priority.ALWAYS);
        HBox pago = new HBox(20, columnaRecibido, columnaCambio);

        VBox cuerpo = new VBox(12, etiquetaSeccion("PRODUCTOS DE LA VENTA"), productos,
                tituloTotal, totalDialogo, separador(), pago);
        cuerpo.setPadding(new Insets(16, 18, 12, 18));
        VBox contenido = new VBox(franja("Confirmar cobro", null), cuerpo);

        Alert dialogo = new Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.OK, ButtonType.CANCEL);
        dialogo.setTitle("Confirmar cobro");
        prepararDialogo(dialogo);
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().setPrefWidth(520);
        ((Button) dialogo.getDialogPane().lookupButton(ButtonType.OK)).setText("Cobrar");
        ((Button) dialogo.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancelar");
        Platform.runLater(() -> {
            recibido.requestFocus();
            recibido.selectAll();
        });

        dialogo.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            BigDecimal efectivo = leerDinero(recibido.getText());
            if (efectivo == null) {
                mostrarError("Cantidad no válida", "El efectivo recibido no es un número válido.");
                return;
            }
            if (efectivo.compareTo(importe) < 0) {
                mostrarError("Pago insuficiente",
                        "La cantidad recibida es menor al total a pagar.");
                return;
            }
            if (!servidorDisponible) {
                carrito.clear();
                actualizarTotal();
                mostrarPedidoCompletado(importe, efectivo, null);
                return;
            }
            String cuerpoVenta;
            try {
                cuerpoVenta = json.writeValueAsString(VentaRequest.desde(carrito, BigDecimal.ZERO));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                mostrarError("No se pudo preparar la venta");
                return;
            }
            request("POST", "/api/ventas", cuerpoVenta).thenAccept(creada -> {
                if (creada.statusCode() != 201) {
                    Platform.runLater(() -> mostrarOperacionFallida(creada));
                    return;
                }
                try {
                    JsonNode venta = json.readTree(creada.body());
                    int id = venta.get("id").asInt();
                    String folio = venta.path("folio").asText(null);
                    String pagoJson = "[{\"forma\":\"EFECTIVO\",\"monto\":" + importe
                            + ",\"recibido\":" + efectivo + "}]";
                    request("POST", "/api/ventas/" + id + "/cobro", pagoJson).thenAccept(cobrada ->
                            Platform.runLater(() -> {
                                if (cobrada.statusCode() == 200) {
                                    carrito.clear();
                                    actualizarTotal();
                                    mostrarPedidoCompletado(importe, efectivo, folio);
                                } else {
                                    mostrarOperacionFallida(cobrada);
                                }
                            }));
                } catch (Exception ex) {
                    Platform.runLater(() -> mostrarError(ex.getMessage()));
                }
            }).exceptionally(ex -> {
                Platform.runLater(() -> mostrarError("Sin conexión",
                        "No se pudo registrar la venta. Verifique la conexión con el servidor."));
                return null;
            });
        });
    }

    private void mostrarPedidoCompletado(BigDecimal importe, BigDecimal efectivo, String folio) {
        BigDecimal cambio = efectivo.subtract(importe);
        Label cambioLabel = new Label("CAMBIO PARA EL CLIENTE");
        cambioLabel.getStyleClass().add("etiqueta-seccion");
        Label cambioMonto = new Label(dinero(cambio));
        cambioMonto.getStyleClass().add("cambio-grande");

        GridPane detalle = new GridPane();
        detalle.setHgap(24);
        detalle.setVgap(6);
        detalle.getStyleClass().add("tarjeta");
        int fila = 0;
        if (folio != null && !folio.isBlank()) {
            detalle.addRow(fila++, textoResumen("Folio"), textoResumen(folio));
        }
        detalle.addRow(fila++, textoResumen("Total pagado"), textoResumen(dinero(importe)));
        detalle.addRow(fila, textoResumen("Efectivo recibido"), textoResumen(dinero(efectivo)));

        VBox cuerpo = new VBox(10, cambioLabel, cambioMonto, detalle);
        cuerpo.setPadding(new Insets(16, 18, 12, 18));
        VBox contenido = new VBox(franja("Venta completada", "franja-exito"), cuerpo);

        Alert dialogo = new Alert(Alert.AlertType.INFORMATION);
        dialogo.setTitle("Venta completada");
        prepararDialogo(dialogo);
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().setPrefWidth(420);
        dialogo.showAndWait();
    }

    private void revisarInventario() {
        if (!servidorDisponible) {
            mostrarInventarioTemporal();
            return;
        }
        request("GET", "/api/productos?q=", null).thenAccept(respuesta ->
                Platform.runLater(() -> {
                    if (respuesta.statusCode() != 200) {
                        mostrarError(mensajeRespuesta(respuesta));
                        return;
                    }
                    try {
                        mostrarInventario(json.readTree(respuesta.body()));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                        mostrarError("No se pudo leer el inventario.");
                    }
                })).exceptionally(ex -> {
                    Platform.runLater(() -> mostrarError("Sin conexión",
                            "No se pudo consultar el inventario en el servidor."));
                    return null;
                });
    }

    private void mostrarInventarioTemporal() {
        List<JsonNode> productos = PRODUCTOS_DE_PRUEBA.stream()
                .map(this::productoJson)
                .toList();
        mostrarInventario(json.valueToTree(productos));
    }

    private void mostrarInventario(JsonNode productos) {
        TableView<InventarioFila> tablaInventario = new TableView<>();
        TableColumn<InventarioFila, String> nombre = new TableColumn<>("PRODUCTO");
        nombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        nombre.setPrefWidth(260);
        TableColumn<InventarioFila, String> codigo = new TableColumn<>("CÓDIGO");
        codigo.setCellValueFactory(new PropertyValueFactory<>("codigo"));
        codigo.setPrefWidth(140);
        TableColumn<InventarioFila, BigDecimal> precio = new TableColumn<>("PRECIO");
        precio.setCellValueFactory(new PropertyValueFactory<>("precio"));
        precio.setCellFactory(columna -> celdaInventarioMonetaria());
        precio.getStyleClass().add("col-dinero");
        TableColumn<InventarioFila, BigDecimal> existencia = new TableColumn<>("EXISTENCIA");
        existencia.setCellValueFactory(new PropertyValueFactory<>("existencia"));
        existencia.setCellFactory(columna -> celdaInventarioNumero());
        existencia.getStyleClass().add("col-centro");
        tablaInventario.getColumns().addAll(nombre, codigo, precio, existencia);
        tablaInventario.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tablaInventario.getSelectionModel().setCellSelectionEnabled(true);
        tablaInventario.addEventHandler(KeyEvent.KEY_PRESSED, evento -> {
            if (evento.isShortcutDown() && evento.getCode() == KeyCode.C) {
                copiarCeldaInventario(tablaInventario);
                evento.consume();
            }
        });
        tablaInventario.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaInventario.setPlaceholder(placeholder("No hay productos registrados", ""));
        tablaInventario.setPrefHeight(340);

        for (JsonNode producto : productos) {
            tablaInventario.getItems().add(new InventarioFila(
                    producto.path("nombre").asText(),
                    producto.path("codigoBarras").isNull() || producto.path("codigoBarras").isMissingNode()
                            ? "—" : producto.path("codigoBarras").asText(),
                    producto.path("precio").decimalValue(),
                    producto.path("stockActual").decimalValue()));
        }

        Label ayuda = new Label("Seleccione una celda y presione Ctrl+C para copiarla");
        ayuda.getStyleClass().add("texto-suave");
        VBox cuerpo = new VBox(10, tablaInventario, ayuda);
        cuerpo.setPadding(new Insets(16, 18, 12, 18));
        VBox.setVgrow(tablaInventario, Priority.ALWAYS);
        VBox contenido = new VBox(franja("Inventario disponible", null), cuerpo);
        VBox.setVgrow(cuerpo, Priority.ALWAYS);

        Alert dialogo = new Alert(Alert.AlertType.INFORMATION);
        dialogo.setTitle("Inventario");
        prepararDialogo(dialogo);
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().setPrefWidth(700);
        ((Button) dialogo.getDialogPane().lookupButton(ButtonType.OK)).setText("Cerrar");
        dialogo.showAndWait();
    }

    private void eliminarSeleccionado() {
        Renglon seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado != null) {
            carrito.remove(seleccionado);
            actualizarTotal();
        }
    }

    private BigDecimal total() {
        return carrito.stream().map(Renglon::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void actualizarTotal() {
        total.setText(dinero(total()));
        BigDecimal unidades = carrito.stream().map(Renglon::getCantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        contadorRenglones.setText(String.valueOf(carrito.size()));
        resumenCarrito.setText(carrito.isEmpty()
                ? "Sin productos"
                : unidades.stripTrailingZeros().toPlainString()
                        + (unidades.compareTo(BigDecimal.ONE) == 0 ? " unidad" : " unidades")
                        + "  ·  " + carrito.size()
                        + (carrito.size() == 1 ? " renglón" : " renglones"));
    }

    /** Formato para mostrar en pantalla: $1,234.50 */
    private static String dinero(BigDecimal valor) {
        return "$" + MONEDA.format(valor.setScale(2, RoundingMode.HALF_UP));
    }

    /** Acepta "500", "500.00", "$500" o "1,500.00". Devuelve null si no es un número. */
    private static BigDecimal leerDinero(String texto) {
        if (texto == null) return null;
        String limpio = texto.replace("$", "").replace(",", "").trim();
        if (limpio.isEmpty()) return null;
        try {
            return new BigDecimal(limpio);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static TableCell<Renglon, BigDecimal> celdaMonetaria() {
        return new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal valor, boolean vacia) {
                super.updateItem(valor, vacia);
                setText(vacia || valor == null ? null : dinero(valor));
            }
        };
    }

    private static TableCell<InventarioFila, BigDecimal> celdaInventarioMonetaria() {
        return new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal valor, boolean vacia) {
                super.updateItem(valor, vacia);
                setText(vacia || valor == null ? null : dinero(valor));
            }
        };
    }

    private static TableCell<InventarioFila, BigDecimal> celdaInventarioNumero() {
        return new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal valor, boolean vacia) {
                super.updateItem(valor, vacia);
                setText(vacia || valor == null ? null : valor.stripTrailingZeros().toPlainString());
            }
        };
    }

    private static void copiarCeldaInventario(TableView<InventarioFila> tablaInventario) {
        if (tablaInventario.getSelectionModel().getSelectedCells().isEmpty()) {
            return;
        }
        TablePosition<?, ?> posicion = tablaInventario.getSelectionModel().getSelectedCells().get(0);
        InventarioFila fila = tablaInventario.getItems().get(posicion.getRow());
        String valor = switch (posicion.getColumn()) {
            case 0 -> fila.getNombre();
            case 1 -> fila.getCodigo();
            case 2 -> fila.getPrecio().setScale(2, RoundingMode.HALF_UP).toPlainString();
            case 3 -> fila.getExistencia().stripTrailingZeros().toPlainString();
            default -> "";
        };
        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(valor);
        Clipboard.getSystemClipboard().setContent(contenido);
    }

    private void verificarServidor() {
        request("GET", "/api/salud", null).thenAccept(r -> Platform.runLater(() -> {
            servidorDisponible = r.statusCode() == 200;
            ponerEstado(servidorDisponible ? "●  Servidor conectado" : "●  Modo local · datos de prueba",
                    servidorDisponible ? "estado-ok" : "estado-local");
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                servidorDisponible = false;
                ponerEstado("●  Modo local · datos de prueba", "estado-local");
            });
            return null;
        });
    }

    private void ponerEstado(String texto, String clase) {
        estadoServidor.setText(texto);
        estadoServidor.getStyleClass().removeAll("estado-ok", "estado-local", "estado-error");
        estadoServidor.getStyleClass().add(clase);
        estadoServidor.setTooltip(new Tooltip(servidorDisponible
                ? "Conectado a " + SERVIDOR
                : "No se pudo conectar a " + SERVIDOR + ". Las ventas no se registran."));
    }

    private java.util.concurrent.CompletableFuture<HttpResponse<String>> request(
            String metodo, String ruta, String cuerpo) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(SERVIDOR + ruta))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
        if ("POST".equals(metodo)) builder.POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        else builder.GET();
        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String texto) {
        return java.net.URLEncoder.encode(texto, java.nio.charset.StandardCharsets.UTF_8);
    }

    private void mostrarError(String mensaje) {
        mostrarError("No se pudo completar la operación",
                mensaje == null ? "Error de comunicación" : mensaje);
    }

    private void mostrarError(String titulo, String mensaje) {
        Label texto = new Label(mensaje);
        texto.getStyleClass().add("mensaje-dialogo");
        texto.setMaxWidth(380);
        VBox cuerpo = new VBox(texto);
        cuerpo.setPadding(new Insets(18));
        VBox contenido = new VBox(franja(titulo, "franja-error"), cuerpo);

        Alert dialogo = new Alert(Alert.AlertType.ERROR);
        dialogo.setTitle(titulo);
        prepararDialogo(dialogo);
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().setPrefWidth(420);
        ((Button) dialogo.getDialogPane().lookupButton(ButtonType.OK)).setText("Entendido");
        dialogo.showAndWait();
    }

    private void mostrarOperacionFallida(HttpResponse<String> respuesta) {
        String titulo = respuesta.statusCode() == 409
                ? "Sin existencias en inventario"
                : "No se pudo completar la operación";
        mostrarError(titulo, mensajeRespuesta(respuesta));
    }

    private String mensajeRespuesta(HttpResponse<String> respuesta) {
        try {
            JsonNode cuerpo = json.readTree(respuesta.body());
            for (String campo : List.of("message", "detail", "error")) {
                JsonNode valor = cuerpo.get(campo);
                if (valor != null && !valor.asText().isBlank()
                        && !"Conflict".equalsIgnoreCase(valor.asText())) {
                    return valor.asText();
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            // Se usa el estado HTTP cuando el servidor no devuelve JSON válido.
        }
        if (respuesta.statusCode() == 409) {
            return "No se puede registrar la venta porque la existencia disponible "
                    + "no cubre la cantidad solicitada. Revise las cantidades del carrito.";
        }
        return "No se pudo completar la operación (HTTP " + respuesta.statusCode() + ").";
    }

    /** Aplica la hoja de estilos, quita el encabezado por defecto y centra el diálogo. */
    private void prepararDialogo(Alert dialogo) {
        dialogo.initOwner(ventanaPrincipal);
        dialogo.setHeaderText(null);
        dialogo.setGraphic(null);
        dialogo.getDialogPane().getStylesheets().add(ESTILO);
        dialogo.setOnShown(evento -> {
            Window ventanaDialogo = dialogo.getDialogPane().getScene().getWindow();
            ventanaDialogo.setX(ventanaPrincipal.getX()
                    + (ventanaPrincipal.getWidth() - ventanaDialogo.getWidth()) / 2);
            ventanaDialogo.setY(ventanaPrincipal.getY()
                    + (ventanaPrincipal.getHeight() - ventanaDialogo.getHeight()) / 2);
        });
    }

    // ---------- Pequeñas piezas de interfaz ----------

    private static Label franja(String texto, String claseExtra) {
        Label franja = new Label(texto);
        franja.getStyleClass().add("franja-dialogo");
        if (claseExtra != null) franja.getStyleClass().add(claseExtra);
        franja.setMaxWidth(Double.MAX_VALUE);
        return franja;
    }

    private static Label etiquetaSeccion(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("etiqueta-seccion");
        return etiqueta;
    }

    private static Label textoResumen(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("renglon-resumen");
        return etiqueta;
    }

    private static Node placeholder(String titulo, String detalle) {
        Label principal = new Label(titulo);
        principal.getStyleClass().add("placeholder-titulo");
        Label secundario = new Label(detalle);
        secundario.getStyleClass().add("placeholder-texto");
        VBox caja = new VBox(4, principal, secundario);
        caja.setAlignment(Pos.CENTER);
        return caja;
    }

    private static Label tecla(String texto) {
        Label tecla = new Label(texto);
        tecla.getStyleClass().add("tecla");
        return tecla;
    }

    private static Label descripcionTecla(String texto) {
        Label descripcion = new Label(texto);
        descripcion.getStyleClass().add("tecla-descripcion");
        return descripcion;
    }

    private static Region separador() {
        Region linea = new Region();
        linea.getStyleClass().add("separador");
        return linea;
    }

    private static Region espacio() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static class Renglon {
        private final int id;
        private final String nombre;
        private final BigDecimal precio;
        private BigDecimal cantidad = BigDecimal.ONE;

        Renglon(int id, String nombre, BigDecimal precio) {
            this.id = id;
            this.nombre = nombre;
            this.precio = precio;
        }
        public BigDecimal getCantidad() { return cantidad; }
        public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
        public String getNombre() { return nombre; }
        public BigDecimal getPrecio() { return precio; }
        public BigDecimal getImporte() { return precio.multiply(cantidad); }
    }

    private record VentaRequest(List<RenglonRequest> detalles, BigDecimal descuento) {
        static VentaRequest desde(List<Renglon> renglones, BigDecimal descuento) {
            return new VentaRequest(
                    renglones.stream().map(r -> new RenglonRequest(r.id, r.cantidad)).toList(),
                    descuento);
        }
    }

    private record RenglonRequest(int productoId, BigDecimal cantidad) {}

    private record ProductoTemporal(int id, String codigo, String nombre, BigDecimal precio, BigDecimal stock) {}

    public static class InventarioFila {
        private final String nombre;
        private final String codigo;
        private final BigDecimal precio;
        private final BigDecimal existencia;

        InventarioFila(String nombre, String codigo, BigDecimal precio, BigDecimal existencia) {
            this.nombre = nombre;
            this.codigo = codigo;
            this.precio = precio;
            this.existencia = existencia;
        }

        public String getNombre() { return nombre; }
        public String getCodigo() { return codigo; }
        public BigDecimal getPrecio() { return precio; }
        public BigDecimal getExistencia() { return existencia; }
    }
}
