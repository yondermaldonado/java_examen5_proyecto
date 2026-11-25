package com.carmotors;

import com.carmotors.servicio.PublicadorOrdenServicio;
import com.carmotors.servicio.NotificacionClienteService;
import com.carmotors.config.ConexionFactory;
import com.carmotors.config.ConfiguracionAplicacion;
import com.carmotors.controlador.AlertasInventarioController;
import com.carmotors.controlador.AsignacionTecnicoController;
import com.carmotors.controlador.ClienteController;
import com.carmotors.controlador.ConsultaLotesController;
import com.carmotors.controlador.ConsultaTallerController;
import com.carmotors.controlador.EvaluacionProveedorController;
import com.carmotors.controlador.FacturaController;
import com.carmotors.controlador.HistorialController;
import com.carmotors.controlador.LoteController;
import com.carmotors.controlador.OperacionOrdenController;
import com.carmotors.controlador.OrdenServicioController;
import com.carmotors.controlador.ProveedorController;
import com.carmotors.controlador.ProveedorRepuestoController;
import com.carmotors.controlador.ReporteController;
import com.carmotors.controlador.RepuestoController;
import com.carmotors.controlador.RepuestoTipoController;
import com.carmotors.controlador.ReservasOrdenController;
import com.carmotors.controlador.TecnicoController;
import com.carmotors.controlador.VehiculoController;
import com.carmotors.dao.OrdenServicioDAO;
import com.carmotors.dao.VehiculoDAO;
import com.carmotors.dao.factory.AlertasDAOFactory;
import com.carmotors.dao.factory.ClienteDAOFactory;
import com.carmotors.dao.factory.ClienteVehiculosDAOFactory;
import com.carmotors.dao.factory.EvaluacionProveedorDAOFactory;
import com.carmotors.dao.factory.ExistenciasDAOFactory;
import com.carmotors.dao.factory.FacturaDAOFactory;
import com.carmotors.dao.factory.HistorialServiciosDAOFactory;
import com.carmotors.dao.factory.InventarioReservasFactory;
import com.carmotors.dao.factory.LoteDAOFactory;
import com.carmotors.dao.factory.OperacionOrdenDAOFactory;
import com.carmotors.dao.factory.OrdenServicioDAOFactory;
import com.carmotors.dao.factory.ProveedorDAOFactory;
import com.carmotors.dao.factory.ProveedorRepuestoDAOFactory;
import com.carmotors.dao.factory.RepuestoDAOFactory;
import com.carmotors.dao.factory.RepuestoTipoDAOFactory;
import com.carmotors.dao.factory.ServicioCatalogoDAOFactory;
import com.carmotors.dao.factory.ServicioTecnicoDAOFactory;
import com.carmotors.dao.factory.TecnicoDAOFactory;
import com.carmotors.dao.factory.VehiculoDAOFactory;
import com.carmotors.servicio.AlertasInventarioService;
import com.carmotors.servicio.AsignacionTecnicoService;
import com.carmotors.servicio.ClienteService;
import com.carmotors.servicio.ConsultaFacturaService;
import com.carmotors.servicio.ConsumoRepuestosStrategy;
import com.carmotors.servicio.EvaluacionProveedorService;
import com.carmotors.servicio.ExistenciasService;
import com.carmotors.servicio.ExportadorFacturaTxt;
import com.carmotors.servicio.FacturacionOrden;
import com.carmotors.servicio.FacturacionService;
import com.carmotors.servicio.HistorialService;
import com.carmotors.servicio.HistorialServiciosConsultaJdbc;
import com.carmotors.servicio.LoteService;
import com.carmotors.servicio.OperacionOrdenService;
import com.carmotors.servicio.OrdenServicioService;
import com.carmotors.servicio.ProductividadTecnicosStrategy;
import com.carmotors.servicio.ProveedorRepuestoService;
import com.carmotors.servicio.ProveedorService;
import com.carmotors.servicio.PublicadorInventario;
import com.carmotors.servicio.RecordatorioPreventivoService;
import com.carmotors.servicio.ReporteStrategy;
import com.carmotors.servicio.RepuestoService;
import com.carmotors.servicio.RepuestoTipoService;
import com.carmotors.servicio.ReservasOrdenService;
import com.carmotors.servicio.ServiciosMasSolicitadosStrategy;
import com.carmotors.servicio.TecnicoService;
import com.carmotors.servicio.VehiculoConsultaImpl;
import com.carmotors.servicio.VehiculoService;
import com.carmotors.vista.Consola;
import com.carmotors.vista.MenuAdministrador;
import com.carmotors.vista.MenuGestionAdministrativa;
import com.carmotors.vista.MenuPrincipal;
import com.carmotors.vista.MenuTecnico;
import com.carmotors.vista.VistaCatalogoProveedorRepuesto;
import com.carmotors.vista.VistaCatalogoRepuestos;
import com.carmotors.vista.VistaClientes;
import com.carmotors.vista.VistaEvaluacionesProveedor;
import com.carmotors.vista.VistaFacturacion;
import com.carmotors.vista.VistaHistorial;
import com.carmotors.vista.VistaInventario;
import com.carmotors.vista.VistaLogin;
import com.carmotors.vista.VistaOperaciones;
import com.carmotors.vista.VistaOrdenes;
import com.carmotors.vista.VistaProveedores;
import com.carmotors.vista.VistaReportes;
import com.carmotors.vista.VistaSupervision;
import com.carmotors.vista.VistaTecnicos;
import com.carmotors.vista.VistaTrabajosTecnico;
import com.carmotors.vista.VistaVehiculos;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Supplier;

/**
 * Punto de composición de la aplicación real.
 * Las vistas no abren conexiones y los DAO/Service se mantienen en sus paquetes.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        Consola consola = new Consola(new Scanner(System.in, StandardCharsets.UTF_8), System.out);
        ensamblar(consola).iniciar();
    }

    public static MenuPrincipal ensamblar(Consola consola) {
        Objects.requireNonNull(consola, "La consola es obligatoria");

        // Los Service ya existentes gestionan el cierre de sus propias conexiones.
        Supplier<Connection> conexiones = ConexionFactory.getInstancia()::crearConexion;
        Clock reloj = Clock.systemDefaultZone();

        var clienteDao = new ClienteDAOFactory().obtenerDAO();
        ClienteService clienteService = new ClienteService(clienteDao,
                new ClienteVehiculosDAOFactory().obtenerDAO());
        ClienteController clienteCtrl = new ClienteController(clienteService);

        VehiculoDAO vehiculoDao = new VehiculoDAOFactory().obtenerDAO();
        VehiculoService vehiculoService = new VehiculoService(vehiculoDao, clienteDao);
        VehiculoController vehiculoCtrl = new VehiculoController(vehiculoService);

        OrdenServicioDAO ordenDao = new OrdenServicioDAOFactory().obtenerDAO();
        PublicadorInventario publicador = new PublicadorInventario();
        // Observer para notificaciones de clientes.
        PublicadorOrdenServicio publicadorOrden =
                new PublicadorOrdenServicio();

        NotificacionClienteService notificacionCliente =
                new NotificacionClienteService();

        publicadorOrden.suscribir(notificacionCliente);
        ReservasOrdenService reservas = new ReservasOrdenService(
                ordenDao, new InventarioReservasFactory().obtenerDAO(), conexiones, reloj, publicador);

        FacturacionOrden facturacion = new FacturacionService(
                ConfiguracionAplicacion.TASA_IMPUESTO);
        OrdenServicioService ordenService = new OrdenServicioService(
                ordenDao, new VehiculoConsultaImpl(vehiculoDao), conexiones, reloj,
                reservas, facturacion, publicador, publicadorOrden);
        OrdenServicioController ordenCtrl = new OrdenServicioController(ordenService);

        // Un solo conjunto de servicios/DAOs sirve a ambas vistas operativas.
        TecnicoService tecnicoService = new TecnicoService(
                new TecnicoDAOFactory(), conexiones);
        TecnicoController tecnicoCtrl = new TecnicoController(tecnicoService);
        AsignacionTecnicoService asignacionService =
                new AsignacionTecnicoService(
                        new ServicioTecnicoDAOFactory(),
                        conexiones,
                        publicadorOrden
                );
        AsignacionTecnicoController asignacionCtrl = new AsignacionTecnicoController(asignacionService);
        ConsultaTallerController consultaTaller = new ConsultaTallerController(
                tecnicoService, asignacionService, ordenService);

        // El mismo ProveedorService consulta MySQL y es reutilizado por lotes y repuestos.
        ProveedorService proveedorService = new ProveedorService(
                new ProveedorDAOFactory(), conexiones);
        ProveedorController proveedorCtrl = new ProveedorController(proveedorService);

        RepuestoService repuestoService = new RepuestoService(
                new RepuestoDAOFactory(), proveedorService, conexiones, publicador);
        RepuestoController repuestoCtrl = new RepuestoController(repuestoService);
        RepuestoTipoService tipoService = new RepuestoTipoService(
                new RepuestoTipoDAOFactory().obtenerDAO(), conexiones);
        RepuestoTipoController tipoCtrl = new RepuestoTipoController(tipoService);

        Supplier<LocalDate> hoy = LocalDate::now;
        LoteService loteService = new LoteService(
                new LoteDAOFactory().obtenerDAO(), proveedorService, conexiones, hoy, publicador);
        LoteController loteCtrl = new LoteController(loteService);
        ExistenciasService existenciasService = new ExistenciasService(
                new ExistenciasDAOFactory().obtenerDAO(), conexiones, hoy);
        AlertasInventarioService alertaService = new AlertasInventarioService(
                new AlertasDAOFactory().obtenerDAO(), existenciasService, conexiones, hoy, 7);
        publicador.suscribir(alertaService);
        AlertasInventarioController alertaCtrl = new AlertasInventarioController(alertaService);
        ReservasOrdenController reservaCtrl = new ReservasOrdenController(reservas);

        VistaInventario inventario = new VistaInventario(
                consola, repuestoCtrl, loteCtrl, proveedorCtrl, reservaCtrl, ordenCtrl, alertaCtrl);
        VistaCatalogoRepuestos catalogo = new VistaCatalogoRepuestos(
                consola, repuestoCtrl, tipoCtrl, proveedorCtrl);

        // El catálogo proveedor-repuesto reutiliza los mismos servicios ya existentes.
        // Se valida la existencia del repuesto consultando al RepuestoService, sin SQL en la vista.
        ProveedorRepuestoService ofertaService = new ProveedorRepuestoService(
                new ProveedorRepuestoDAOFactory(), proveedorService,
                repuestoId -> repuestoId > 0 && !repuestoService.buscarPorId(repuestoId).isEmpty(),
                conexiones);
        ProveedorRepuestoController ofertaCtrl = new ProveedorRepuestoController(ofertaService);
        EvaluacionProveedorService evaluacionService = new EvaluacionProveedorService(
                new EvaluacionProveedorDAOFactory(), conexiones);
        EvaluacionProveedorController evaluacionCtrl = new EvaluacionProveedorController(evaluacionService);
        ConsultaLotesController consultaLotes = new ConsultaLotesController(loteService);

        VistaCatalogoProveedorRepuesto ofertas = new VistaCatalogoProveedorRepuesto(
                consola, ofertaCtrl, proveedorCtrl, repuestoCtrl);
        VistaEvaluacionesProveedor evaluaciones = new VistaEvaluacionesProveedor(
                consola, evaluacionCtrl, proveedorCtrl, consultaLotes);
        VistaProveedores vistaProveedores = new VistaProveedores(
                consola, proveedorCtrl, ofertas, evaluaciones);

        // Historial y recordatorios: consultas reales, sin escrituras desde la vista.
        HistorialService historialService = new HistorialService(vehiculoService,
                new HistorialServiciosConsultaJdbc(
                        new HistorialServiciosDAOFactory().obtenerDAO(), conexiones));
        RecordatorioPreventivoService recordatorioService =
                new RecordatorioPreventivoService(historialService, reloj);
        HistorialController historialCtrl =
                new HistorialController(historialService, recordatorioService);
        VistaHistorial vistaHistorial = new VistaHistorial(
                consola, historialCtrl, clienteCtrl, vehiculoCtrl);

        var operacionesService = new OperacionOrdenService(
                new OperacionOrdenDAOFactory().obtenerDAO(),
                new ServicioCatalogoDAOFactory().obtenerDAO(), ordenDao, conexiones);
        var operaciones = new VistaOperaciones(consola,
                new OperacionOrdenController(operacionesService));
        var facturaCtrl = new FacturaController(
                new ConsultaFacturaService(
                        new FacturaDAOFactory().obtenerDAO(), conexiones,
                        new ExportadorFacturaTxt()));

        VistaClientes clientes = new VistaClientes(consola, clienteCtrl);
        VistaTecnicos vistaTecnicos = new VistaTecnicos(consola, tecnicoCtrl, consultaTaller);
        VistaVehiculos vehiculos = new VistaVehiculos(consola, vehiculoCtrl, clienteCtrl);
        VistaOrdenes ordenes = new VistaOrdenes(consola, ordenCtrl, vehiculoCtrl, operaciones);
        VistaFacturacion vistaFacturacion = new VistaFacturacion(consola, ordenCtrl, facturaCtrl,
                ConfiguracionAplicacion.CARPETA_FACTURAS.toString());
        VistaSupervision supervision = new VistaSupervision(consola, consultaTaller, asignacionCtrl);
        VistaTrabajosTecnico trabajos = new VistaTrabajosTecnico(consola, consultaTaller, ordenCtrl, operaciones);

        // Las estrategias existentes consultan MySQL mediante sus DAO y ConexionFactory.
        // El controller únicamente adapta los filtros de fecha y orden.
        ReporteController reporteCtrl = new ReporteController(List.<ReporteStrategy>of(
                new ServiciosMasSolicitadosStrategy(),
                new ProductividadTecnicosStrategy(),
                new ConsumoRepuestosStrategy()));
        VistaReportes reportes = new VistaReportes(consola, reporteCtrl);

        VistaLogin login = new VistaLogin(consola);
        MenuGestionAdministrativa gestion = new MenuGestionAdministrativa(
                consola, clientes, vehiculos, vistaTecnicos, catalogo, vistaProveedores);
        MenuAdministrador admin = new MenuAdministrador(
                consola, gestion, ordenes, supervision, inventario, vistaFacturacion, vistaHistorial, reportes);
        MenuTecnico tecnico = new MenuTecnico(
                consola, clientes, vehiculos, ordenes, trabajos, inventario, vistaHistorial);
        return new MenuPrincipal(consola, login, admin, tecnico);
    }
}
