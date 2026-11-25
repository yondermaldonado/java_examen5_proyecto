package com.carmotors.servicio;

import com.carmotors.modelo.EventoOrdenServicio;

import java.util.ArrayList;
import java.util.List;

public class PublicadorOrdenServicio {

    private final List<ObservadorCliente> observadores =
            new ArrayList<>();

    // Agregar un observador.
    public void suscribir(ObservadorCliente observador) {

        if (observador != null
                && !observadores.contains(observador)) {

            observadores.add(observador);
        }
    }

    // Eliminar un observador.
    public void desuscribir(ObservadorCliente observador) {

        observadores.remove(observador);
    }

    // Notificar a todos los observadores.
    public void publicar(EventoOrdenServicio evento) {

        for (ObservadorCliente observador : observadores) {

            try {

                observador.actualizar(evento);

            } catch (RuntimeException e) {

                System.err.println(
                        "Error al enviar la notificación: "
                                + e.getMessage()
                );
            }
        }
    }
}