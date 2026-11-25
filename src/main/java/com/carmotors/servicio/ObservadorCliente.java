package com.carmotors.servicio;

import com.carmotors.modelo.EventoOrdenServicio;

public interface ObservadorCliente {

    void actualizar(EventoOrdenServicio evento);
}