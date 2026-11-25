package com.carmotors.servicio;

import com.carmotors.dao.OrdenServicioDAO;
import com.carmotors.dao.factory.DAOFactory;
import com.carmotors.excepcion.DaoException;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.OrdenServicio;
import com.carmotors.modelo.Vehiculo;
import com.carmotors.modelo.EventoOrdenServicio;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Alta, consulta y ciclo de vida de órdenes;
 * consumo/facturación mediante contratos transaccionales.
 */
public class OrdenServicioService {

    private final OrdenServicioDAO dao;
    private final VehiculoConsulta vehiculos;
    private final Supplier<Connection> conexiones;
    private final Clock reloj;
    private final ConsumoReservasOrden consumo;
    private final FacturacionOrden facturacion;
    private final PublicadorInventario publicador;

    // NUEVO: publicador para notificaciones de clientes.
    private final PublicadorOrdenServicio publicadorOrden;

    private static final Map<String, String> SIGUIENTE = Map.of(
            "Pendiente", "En Proceso",
            "En Proceso", "Completado",
            "Completado", "Facturado",
            "Facturado", "Entregado"
    );

    public OrdenServicioService(
            DAOFactory<OrdenServicioDAO> creador,
            VehiculoConsulta vehiculos,
            Supplier<Connection> conexiones,
            Clock reloj
    ) {
        this(creador.obtenerDAO(), vehiculos, conexiones, reloj);
    }

    public OrdenServicioService(
            OrdenServicioDAO dao,
            VehiculoConsulta vehiculos,
            Supplier<Connection> conexiones,
            Clock reloj
    ) {
        this(dao, vehiculos, conexiones, reloj,
                (con, id) -> {
                    throw new ValidacionException(
                            "El consumo de reservas aún no está configurado."
                    );
                },
                (con, id, fecha) -> {
                    throw new ValidacionException(
                            "La facturación aún no está configurada."
                    );
                },
                new PublicadorInventario()
        );
    }

    public OrdenServicioService(
            DAOFactory<OrdenServicioDAO> creador,
            VehiculoConsulta vehiculos,
            Supplier<Connection> conexiones,
            Clock reloj,
            ConsumoReservasOrden consumo,
            FacturacionOrden facturacion,
            PublicadorInventario publicador
    ) {
        this(creador.obtenerDAO(), vehiculos, conexiones,
                reloj, consumo, facturacion, publicador);
    }

    /**
     * Habilita consumo real dejando facturación bloqueada
     * hasta integrar su módulo.
     */
    public OrdenServicioService(
            DAOFactory<OrdenServicioDAO> creador,
            VehiculoConsulta vehiculos,
            Supplier<Connection> conexiones,
            Clock reloj,
            ConsumoReservasOrden consumo,
            PublicadorInventario publicador
    ) {
        this(creador.obtenerDAO(), vehiculos, conexiones,
                reloj, consumo,
                (con, id, fecha) -> {
                    throw new ValidacionException(
                            "La facturación aún no está configurada."
                    );
                },
                publicador
        );
    }

    // Constructor original compatible.
    public OrdenServicioService(
            OrdenServicioDAO dao,
            VehiculoConsulta vehiculos,
            Supplier<Connection> conexiones,
            Clock reloj,
            ConsumoReservasOrden consumo,
            FacturacionOrden facturacion,
            PublicadorInventario publicador
    ) {
        this(dao, vehiculos, conexiones, reloj,
                consumo, facturacion, publicador,
                new PublicadorOrdenServicio());
    }

    // NUEVO: constructor con Factory y Observer de clientes.
    public OrdenServicioService(
            DAOFactory<OrdenServicioDAO> creador,
            VehiculoConsulta vehiculos,
            Supplier<Connection> conexiones,
            Clock reloj,
            ConsumoReservasOrden consumo,
            FacturacionOrden facturacion,
            PublicadorInventario publicador,
            PublicadorOrdenServicio publicadorOrden
    ) {
        this(creador.obtenerDAO(), vehiculos, conexiones,
                reloj, consumo, facturacion,
                publicador, publicadorOrden);
    }

    // NUEVO: constructor principal con ambos publicadores.
    public OrdenServicioService(
            OrdenServicioDAO dao,
            VehiculoConsulta vehiculos,
            Supplier<Connection> conexiones,
            Clock reloj,
            ConsumoReservasOrden consumo,
            FacturacionOrden facturacion,
            PublicadorInventario publicador,
            PublicadorOrdenServicio publicadorOrden
    ) {

        this.consumo = Objects.requireNonNull(consumo);
        this.facturacion = Objects.requireNonNull(facturacion);
        this.publicador = Objects.requireNonNull(publicador);
        this.dao = Objects.requireNonNull(dao);
        this.vehiculos = Objects.requireNonNull(vehiculos);
        this.conexiones = Objects.requireNonNull(conexiones);
        this.reloj = Objects.requireNonNull(reloj);

        // NUEVO: inyección del Observer de clientes.
        this.publicadorOrden =
                Objects.requireNonNull(publicadorOrden);
    }

    public OrdenServicio crearOrden(
            int vehiculoId,
            String mantenimiento,
            String problema
    ) {

        validarId(vehiculoId);

        String tipo = opcion(
                mantenimiento,
                List.of("Preventivo", "Correctivo"),
                "tipo de mantenimiento"
        );

        if (problema == null || problema.isBlank()) {
            throw new ValidacionException(
                    "La descripción del problema es obligatoria."
            );
        }

        String descripcion = problema.strip();

        if (descripcion.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new ValidacionException(
                    "La descripción supera la capacidad del campo TEXT (65535 bytes UTF-8)."
            );
        }

        Vehiculo vehiculo = vehiculos.buscarPorId(vehiculoId)
                .orElseThrow(() -> new ValidacionException(
                        "No existe el vehículo indicado."
                ));

        OrdenServicio orden = new OrdenServicio();

        orden.setVehiculoId(vehiculoId);
        orden.setClienteId(vehiculo.getClienteId());
        orden.setPlaca(vehiculo.getPlaca());
        orden.setTipoMantenimiento(tipo);
        orden.setEstado("Pendiente");
        orden.setFechaRecepcion(
                LocalDateTime.now(reloj).withNano(0)
        );
        orden.setDescripcionProblema(descripcion);

        // Una orden nueva no tiene operaciones cobradas.
        orden.setCostoManoObra(new BigDecimal("0.00"));

        int id;

        try (Connection con = conexiones.get()) {

            con.setAutoCommit(false);

            try {

                id = dao.crear(con, orden);

                con.commit();

                orden.setId(id);

            } catch (RuntimeException | SQLException e) {

                try {
                    con.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }

                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }

                throw new DaoException(
                        "No se pudo confirmar el registro de la orden.",
                        e
                );
            }

        } catch (SQLException e) {

            throw new DaoException(
                    "No se pudo administrar la conexión de órdenes.",
                    e
            );
        }

        // NUEVO: notificar después de confirmar la creación.
        publicadorOrden.publicar(
                new EventoOrdenServicio(id, "Pendiente")
        );

        return orden;
    }

    public void iniciarOrden(int id) {
        cambiarEstado(id, "En Proceso");
    }

    public void completarOrden(int id) {
        cambiarEstado(id, "Completado");
    }

    public void facturarOrden(int id) {
        cambiarEstado(id, "Facturado");
    }

    public void entregarOrden(int id) {
        cambiarEstado(id, "Entregado");
    }

    public void cambiarEstado(int id, String destino) {

        validarId(id);

        String nuevo = opcion(
                destino,
                List.of(
                        "Pendiente",
                        "En Proceso",
                        "Completado",
                        "Facturado",
                        "Entregado"
                ),
                "estado"
        );

        Set<Integer> afectados = Set.of();

        try (Connection con = conexiones.get()) {

            con.setAutoCommit(false);

            try {

                OrdenServicio orden = dao.bloquearPorId(con, id)
                        .orElseThrow(() -> new ValidacionException(
                                "No existe la orden indicada."
                        ));

                if (!nuevo.equals(SIGUIENTE.get(orden.getEstado()))) {

                    throw new ValidacionException(
                            "Transición no permitida: "
                                    + orden.getEstado()
                                    + " → " + nuevo + "."
                    );
                }

                LocalDateTime fecha =
                        LocalDateTime.now(reloj).withNano(0);

                if (fecha.isBefore(orden.getFechaRecepcion())
                        || (orden.getFechaCompletado() != null
                        && fecha.isBefore(orden.getFechaCompletado()))) {

                    throw new ValidacionException(
                            "La fecha de la operación no puede ser anterior a las fechas de la orden."
                    );
                }

                if ("Completado".equals(nuevo)) {

                    afectados = Set.copyOf(
                            consumo.consumir(con, id)
                    );

                    if (!dao.consumoCompleto(con, id)) {

                        throw new ValidacionException(
                                "No se puede completar: faltan consumos o quedan reservas activas."
                        );
                    }

                } else if ("Facturado".equals(nuevo)) {

                    if (dao.existeFactura(con, id)) {

                        throw new ValidacionException(
                                "La orden ya tiene una factura."
                        );
                    }

                    facturacion.facturar(con, id, fecha);

                    if (!dao.existeFactura(con, id)) {

                        throw new ValidacionException(
                                "No se generó la factura de la orden."
                        );
                    }

                } else if ("Entregado".equals(nuevo)
                        && !dao.existeFactura(con, id)) {

                    throw new ValidacionException(
                            "No se puede entregar una orden sin factura persistida."
                    );
                }

                if (!dao.actualizarEstado(
                        con,
                        id,
                        orden.getEstado(),
                        nuevo,
                        fecha
                )) {

                    throw new ValidacionException(
                            "El estado de la orden cambió; vuelve a consultarla."
                    );
                }

                con.commit();

            } catch (RuntimeException | SQLException e) {

                try {
                    con.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }

                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }

                throw new DaoException(
                        "No se pudo confirmar la transición de la orden.",
                        e
                );
            }

        } catch (SQLException e) {

            throw new DaoException(
                    "No se pudo administrar la conexión de la transición.",
                    e
            );
        }

        // Observer original: notificaciones de inventario.
        afectados.forEach(publicador::publicar);

        // NUEVO: Observer de clientes.
        // Solo se ejecuta después de confirmar la transacción.
        publicadorOrden.publicar(
                new EventoOrdenServicio(id, nuevo)
        );
    }

    public Optional<OrdenServicio> buscarPorId(int id) {

        validarId(id);

        return consultar(con -> dao.buscarPorId(con, id));
    }

    public List<OrdenServicio> listar() {

        return consultar(dao::listar);
    }

    public List<OrdenServicio> listarPorVehiculo(int vehiculoId) {

        validarId(vehiculoId);

        return consultar(
                con -> dao.listarPorVehiculo(con, vehiculoId)
        );
    }

    public List<OrdenServicio> listarPorEstado(String estado) {

        String valido = opcion(
                estado,
                List.of(
                        "Pendiente",
                        "En Proceso",
                        "Completado",
                        "Facturado",
                        "Entregado"
                ),
                "estado"
        );

        return consultar(
                con -> dao.listarPorEstado(con, valido)
        );
    }

    private String opcion(
            String valor,
            List<String> permitidos,
            String campo
    ) {

        if (valor == null) {

            throw new ValidacionException(
                    "Debes indicar el " + campo + "."
            );
        }

        return permitidos.stream()
                .filter(p -> p.equalsIgnoreCase(valor.strip()))
                .findFirst()
                .orElseThrow(() -> new ValidacionException(
                        "El " + campo + " debe ser: "
                                + String.join(", ", permitidos)
                                + "."
                ));
    }

    private void validarId(int id) {

        if (id <= 0) {

            throw new ValidacionException(
                    "El identificador debe ser mayor que cero."
            );
        }
    }

    private <T> T consultar(Function<Connection, T> accion) {

        try (Connection con = conexiones.get()) {

            return accion.apply(con);

        } catch (SQLException e) {

            throw new DaoException(
                    "No se pudo cerrar la conexión al consultar órdenes.",
                    e
            );
        }
    }
}