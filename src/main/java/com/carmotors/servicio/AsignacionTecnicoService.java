package com.carmotors.servicio;

import com.carmotors.dao.ServicioTecnicoDAO;
import com.carmotors.dao.factory.DAOFactory;
import com.carmotors.excepcion.DaoException;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.ServicioTecnico;
import com.carmotors.modelo.CargaTecnico;
import com.carmotors.modelo.EventoOrdenServicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Asignaciones N:M; cada escritura bloquea
 * primero la orden y luego el técnico.
 */
public class AsignacionTecnicoService {

    private final ServicioTecnicoDAO dao;
    private final Supplier<Connection> conexiones;

    // NUEVO: publicador de notificaciones para clientes.
    private final PublicadorOrdenServicio publicadorOrden;

    // Constructor original.
    public AsignacionTecnicoService(
            DAOFactory<ServicioTecnicoDAO> creador,
            Supplier<Connection> conexiones
    ) {
        this(creador.obtenerDAO(), conexiones);
    }

    // Constructor original compatible.
    public AsignacionTecnicoService(
            ServicioTecnicoDAO dao,
            Supplier<Connection> conexiones
    ) {
        this(dao, conexiones, new PublicadorOrdenServicio());
    }

    // NUEVO: constructor con Factory y Observer.
    public AsignacionTecnicoService(
            DAOFactory<ServicioTecnicoDAO> creador,
            Supplier<Connection> conexiones,
            PublicadorOrdenServicio publicadorOrden
    ) {
        this(creador.obtenerDAO(), conexiones, publicadorOrden);
    }

    // NUEVO: constructor principal.
    public AsignacionTecnicoService(
            ServicioTecnicoDAO dao,
            Supplier<Connection> conexiones,
            PublicadorOrdenServicio publicadorOrden
    ) {
        this.dao = Objects.requireNonNull(dao);
        this.conexiones = Objects.requireNonNull(conexiones);
        this.publicadorOrden =
                Objects.requireNonNull(publicadorOrden);
    }

    public void asignar(int ordenId, int tecnicoId) {

        ids(ordenId, tecnicoId);

        // NUEVO: determinar si realmente cambió el estado.
        boolean cambioEstado = transaccion(con -> {

            String estadoAnterior =
                    dao.bloquearEstadoOrden(con, ordenId)
                            .orElseThrow(() ->
                                    new ValidacionException(
                                            "No existe la orden indicada."
                                    ));

            boolean activo =
                    validarReferencias(con, ordenId, tecnicoId);

            if (!activo) {
                throw new ValidacionException(
                        "El técnico está inactivo y no puede recibir asignaciones nuevas."
                );
            }

            if (dao.existe(con, ordenId, tecnicoId)) {
                throw new ValidacionException(
                        "El técnico ya está asignado a esta orden."
                );
            }

            dao.asignar(con, ordenId, tecnicoId);

            dao.iniciarOrden(con, ordenId);

            // NUEVO: consultar el estado después de iniciar.
            String estadoActual =
                    dao.bloquearEstadoOrden(con, ordenId)
                            .orElseThrow(() ->
                                    new ValidacionException(
                                            "No existe la orden indicada."
                                    ));

            // Solo notificar si pasó de Pendiente a En Proceso.
            return "Pendiente".equals(estadoAnterior)
                    && "En Proceso".equals(estadoActual);
        });

        // NUEVO: publicar después de confirmar la transacción.
        if (cambioEstado) {

            publicadorOrden.publicar(
                    new EventoOrdenServicio(
                            ordenId,
                            "En Proceso"
                    )
            );
        }
    }

    public void quitar(int ordenId, int tecnicoId) {

        ids(ordenId, tecnicoId);

        transaccion(con -> {

            validarReferencias(con, ordenId, tecnicoId);

            if (!dao.quitar(con, ordenId, tecnicoId)) {
                throw new ValidacionException(
                        "El técnico no está asignado a esta orden."
                );
            }

            return null;
        });
    }

    /**
     * Suma una duración HH:mm al tiempo existente
     * de esta asignación.
     */
    public void registrarTiempo(
            int ordenId,
            int tecnicoId,
            String tiempo
    ) {
        registrarMinutos(
                ordenId,
                tecnicoId,
                TiempoTrabajo.leerMinutos(tiempo)
        );
    }

    /**
     * Compatibilidad para consumidores internos
     * que todavía usan horas decimales.
     */
    public void registrarHoras(
            int ordenId,
            int tecnicoId,
            BigDecimal horas
    ) {

        if (horas == null
                || horas.signum() < 0
                || horas.compareTo(new BigDecimal("999.99")) > 0) {

            throw new ValidacionException(
                    "Las horas deben estar entre 0 y 999.99."
            );
        }

        try {

            horas.setScale(2, RoundingMode.UNNECESSARY);

        } catch (ArithmeticException e) {

            throw new ValidacionException(
                    "Las horas admiten como máximo dos decimales."
            );
        }

        registrarMinutos(
                ordenId,
                tecnicoId,
                TiempoTrabajo.minutos(horas)
        );
    }

    private void registrarMinutos(
            int ordenId,
            int tecnicoId,
            int minutosNuevos
    ) {

        ids(ordenId, tecnicoId);

        transaccion(con -> {

            validarReferencias(con, ordenId, tecnicoId);

            BigDecimal anteriores =
                    dao.bloquearHoras(con, ordenId, tecnicoId)
                            .orElseThrow(() ->
                                    new ValidacionException(
                                            "El técnico no está asignado a esta orden."
                                    ));

            // Evitar acumular redondeos de centésimas de hora.
            int totalMinutos =
                    TiempoTrabajo.minutos(anteriores)
                            + minutosNuevos;

            if (totalMinutos > TiempoTrabajo.MAXIMO_MINUTOS) {

                throw new ValidacionException(
                        "El tiempo acumulado no puede superar 999:59."
                );
            }

            dao.actualizarHoras(
                    con,
                    ordenId,
                    tecnicoId,
                    TiempoTrabajo.horas(totalMinutos)
            );

            return null;
        });
    }

    public List<CargaTecnico> listarCargaEnProceso() {

        return consultar(dao::listarCargaEnProceso);
    }

    public List<CargaTecnico> listarSinOrdenesAbiertas() {

        return consultar(dao::listarSinOrdenesAbiertas);
    }

    public List<ServicioTecnico> listarPorOrden(int id) {

        id(id);

        return consultar(
                con -> dao.listarPorOrden(con, id)
        );
    }

    public List<ServicioTecnico> listarPorTecnico(int id) {

        id(id);

        return consultar(
                con -> dao.listarPorTecnico(con, id)
        );
    }

    private boolean validarReferencias(
            Connection con,
            int ordenId,
            int tecnicoId
    ) {

        String estado =
                dao.bloquearEstadoOrden(con, ordenId)
                        .orElseThrow(() ->
                                new ValidacionException(
                                        "No existe la orden indicada."
                                ));

        if (!List.of("Pendiente", "En Proceso").contains(estado)) {

            throw new ValidacionException(
                    "La orden está cerrada o su estado no permite modificar asignaciones."
            );
        }

        return dao.bloquearTecnicoActivo(con, tecnicoId)
                .orElseThrow(() ->
                        new ValidacionException(
                                "No existe el técnico indicado."
                        ));
    }

    private void ids(int orden, int tecnico) {

        id(orden);
        id(tecnico);
    }

    private void id(int id) {

        if (id <= 0) {

            throw new ValidacionException(
                    "Los identificadores deben ser mayores que cero."
            );
        }
    }

    private <T> T consultar(Function<Connection, T> accion) {

        try (Connection con = conexiones.get()) {

            return accion.apply(con);

        } catch (SQLException e) {

            throw new DaoException(
                    "No se pudo cerrar la conexión de asignaciones.",
                    e
            );
        }
    }

    private <T> T transaccion(Function<Connection, T> accion) {

        try (Connection con = conexiones.get()) {

            con.setAutoCommit(false);

            try {

                T resultado = accion.apply(con);

                con.commit();

                return resultado;

            } catch (RuntimeException | SQLException e) {

                try {

                    con.rollback();

                } catch (SQLException rollback) {

                    e.addSuppressed(rollback);
                }

                for (Throwable causa = e;
                     causa != null;
                     causa = causa.getCause()) {

                    if (causa instanceof SQLException sql
                            && sql.getErrorCode() == 1062) {

                        throw new ValidacionException(
                                "El técnico ya está asignado a esta orden."
                        );
                    }
                }

                if (e instanceof RuntimeException runtime) {

                    throw runtime;
                }

                throw new DaoException(
                        "No se pudo confirmar la asignación.",
                        e
                );
            }

        } catch (SQLException e) {

            throw new DaoException(
                    "No se pudo administrar la conexión de asignaciones.",
                    e
            );
        }
    }
}