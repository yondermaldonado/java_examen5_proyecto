package com.carmotors.servicio;

import com.carmotors.modelo.EventoOrdenServicio;

public class NotificacionClienteService
        implements ObservadorCliente {

    @Override
    public void actualizar(EventoOrdenServicio evento) {

        String mensaje = switch (evento.getEstado()) {

            case "Pendiente" ->
                    "Su orden de servicio ha sido registrada.";

            case "En Proceso" ->
                    "Hemos iniciado el mantenimiento de su vehículo.";

            case "Completado" ->
                    "El mantenimiento de su vehículo ha finalizado.";

            case "Facturado" ->
                    "Su factura está disponible.";

            case "Entregado" ->
                    "Su vehículo ha sido entregado correctamente.";

            default ->
                    "El estado de su orden ha cambiado.";
        };

        System.out.println();
        System.out.println("========== NOTIFICACIÓN AL CLIENTE ==========");

        System.out.println("Orden: #" + evento.getIdOrden());

        System.out.println("Estado: " + evento.getEstado());

        System.out.println("Mensaje: " + mensaje);

        System.out.println("=============================================");
    }
}