package com.carmotors.modelo;

public class EventoOrdenServicio {

    private final long idOrden;
    private final String estado;

    public EventoOrdenServicio(long idOrden, String estado) {
        this.idOrden = idOrden;
        this.estado = estado;
    }

    public long getIdOrden() {
        return idOrden;
    }

    public String getEstado() {
        return estado;
    }
}