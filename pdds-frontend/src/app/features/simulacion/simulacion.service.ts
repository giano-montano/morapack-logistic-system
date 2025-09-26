import { Injectable } from '@angular/core';
import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {catchError, Observable, of, throwError} from 'rxjs';
import { environment } from 'src/environments/environment';
// ===========================
// INTERFACES / MODELOS
// ===========================
export interface Ruta {
  id: number;
  origen: string;
  destino: string;
  duracion: number;
  distancia?: number;
  continente: string;
}

export interface Pedido {
  id: number;
  rutaId: number;
  fecha: string;
  cantidad: number;
  prioridad?: string;
}

export interface Vuelo {
  codigoVuelo: string;
  origen: string;
  destino: string;
  horaSalida: string;
  horaLlegada: string;
  cantidadPaquetes?: number;
  capacidadMaxima?: number;
  estado?: string;
}

export interface SimulacionResponse {
  exitoso: boolean;
  mensaje: string;
  pedidoId: number;
  vuelos: Vuelo[];
  tiempoTotal?: number;
  costoTotal?: number;
  rutaCompleta?: string[];
}
@Injectable({
  providedIn: 'root'
})
export class SimulacionService {

  private readonly baseUrl = environment.apiUrl;
  // Datos mock para desarrollo/testing
  private readonly rutasMock: Ruta[] = [
    {
      id: 1,
      origen: 'Lima',
      destino: 'Cusco',
      duracion: 120,
      distancia: 1105,
      continente: 'America'
    },
    {
      id: 2,
      origen: 'Lima',
      destino: 'Arequipa',
      duracion: 90,
      distancia: 764,
      continente: 'America'
    },
    {
      id: 3,
      origen: 'Bruselas',
      destino: 'Lima',
      duracion: 720,
      distancia: 10234,
      continente: 'Intercontinental'
    },
    {
      id: 4,
      origen: 'Baku',
      destino: 'Bruselas',
      duracion: 360,
      distancia: 4234,
      continente: 'Europa-Asia'
    },
    {
      id: 5,
      origen: 'Lima',
      destino: 'Puno',
      duracion: 150,
      distancia: 876,
      continente: 'America'
    }
  ];
  constructor(private http: HttpClient) {}

  // Obtener todas las rutas disponibles
  /*getRutas(): Observable<Ruta[]> {
    return this.http.get<Ruta[]>(`${this.baseUrl}/rutas`);
  }

  // Simular un pedido
  simularPedido(pedido: Pedido): Observable<SimulacionResponse> {
    return this.http.post<SimulacionResponse>(`${this.baseUrl}/simular`, pedido);
  }*/
  // constructor(private http: HttpClient) {} // descomenta cuando uses API real
  /**
   * Obtiene todas las rutas disponibles
   */
  getRutas(): Observable<Ruta[]> {
    return this.http.get<Ruta[]>(`${this.baseUrl}/rutas`)
      .pipe(
        catchError((error) => {
          console.warn('API no disponible, usando datos mock para rutas');
          return of(this.rutasMock);
        })
      );
  }

  /**
   * Simula un pedido y retorna la planificación de vuelos
   */
  simularPedido(pedido: Pedido): Observable<SimulacionResponse> {
    const url = `${this.baseUrl}/simulacion/simular`;

    return this.http.post<SimulacionResponse>(url, pedido)
      .pipe(
        catchError((error) => {
          console.warn('API no disponible, usando simulación mock');
          return of(this.generarSimulacionMock(pedido));
        })
      );
  }

  /**
   * Obtiene el estado de un pedido específico
   */
  obtenerEstadoPedido(pedidoId: number): Observable<SimulacionResponse> {
    return this.http.get<SimulacionResponse>(`${this.baseUrl}/simulacion/estado/${pedidoId}`)
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Cancela una simulación en curso
   */
  cancelarSimulacion(pedidoId: number): Observable<boolean> {
    return this.http.delete<boolean>(`${this.baseUrl}/simulacion/cancelar/${pedidoId}`)
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Genera una simulación mock para pruebas
   */
  private generarSimulacionMock(pedido: Pedido): SimulacionResponse {
    const rutaSeleccionada = this.rutasMock.find(r => r.id === pedido.rutaId);

    if (!rutaSeleccionada) {
      return {
        exitoso: false,
        mensaje: 'Ruta no encontrada',
        pedidoId: pedido.id,
        vuelos: []
      };
    }

    // Simular vuelos según la cantidad de paquetes
    const vuelosNecesarios = Math.ceil(pedido.cantidad / 250); // Capacidad promedio por vuelo
    const vuelos: Vuelo[] = [];

    for (let i = 0; i < vuelosNecesarios; i++) {
      const paquetesEnVuelo = Math.min(250, pedido.cantidad - (i * 250));
      const horaSalida = this.calcularHoraSalida(pedido.fecha, i);
      const horaLlegada = this.calcularHoraLlegada(horaSalida, rutaSeleccionada.duracion);

      vuelos.push({
        codigoVuelo: `MP-${String(1000 + i).padStart(4, '0')}`,
        origen: rutaSeleccionada.origen,
        destino: rutaSeleccionada.destino,
        horaSalida,
        horaLlegada,
        cantidadPaquetes: paquetesEnVuelo,
        capacidadMaxima: 250,
        estado: 'Programado'
      });
    }

    return {
      exitoso: true,
      mensaje: `Simulación exitosa para ${pedido.cantidad} paquetes`,
      pedidoId: pedido.id,
      vuelos,
      tiempoTotal: rutaSeleccionada.duracion / 60, // En horas
      costoTotal: this.calcularCosto(pedido.cantidad, rutaSeleccionada),
      rutaCompleta: [rutaSeleccionada.origen, rutaSeleccionada.destino]
    };
  }

  /**
   * Calcula la hora de salida basada en la fecha del pedido
   */
  private calcularHoraSalida(fecha: string, indiceVuelo: number): string {
    const fechaPedido = new Date(fecha);
    fechaPedido.setHours(8 + (indiceVuelo * 2)); // Vuelos cada 2 horas desde las 8 AM
    return fechaPedido.toLocaleString('es-PE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  /**
   * Calcula la hora de llegada
   */
  private calcularHoraLlegada(horaSalida: string, duracionMinutos: number): string {
    // Parsear la hora de salida
    const [fecha, hora] = horaSalida.split(' ');
    const [dia, mes, año] = fecha.split('/');
    const [horas, minutos] = hora.split(':');

    const fechaSalida = new Date(parseInt(año), parseInt(mes) - 1, parseInt(dia),
      parseInt(horas), parseInt(minutos));

    // Agregar duración
    const fechaLlegada = new Date(fechaSalida.getTime() + (duracionMinutos * 60000));

    return fechaLlegada.toLocaleString('es-PE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  /**
   * Calcula el costo total del envío
   */
  private calcularCosto(cantidad: number, ruta: Ruta): number {
    const costoPorPaquete = ruta.continente === 'Intercontinental' ? 25 : 15;
    const costoBase = cantidad * costoPorPaquete;
    const descuento = cantidad > 100 ? 0.1 : 0; // 10% descuento para pedidos grandes

    return Math.round(costoBase * (1 - descuento));
  }

  /**
   * Maneja errores HTTP
   */
  private handleError = (error: HttpErrorResponse): Observable<never> => {
    let errorMessage = 'Error desconocido';

    if (error.error instanceof ErrorEvent) {
      // Error del cliente
      errorMessage = `Error: ${error.error.message}`;
    } else {
      // Error del servidor
      errorMessage = `Código de error: ${error.status}\nMensaje: ${error.message}`;
    }

    console.error('Error en SimulacionService:', errorMessage);
    return throwError(() => new Error(errorMessage));
  }

  /**
   * Valida los datos del pedido antes de enviar
   */
  validarPedido(pedido: Pedido): { valido: boolean; errores: string[] } {
    const errores: string[] = [];

    if (!pedido.rutaId || pedido.rutaId <= 0) {
      errores.push('Debe seleccionar una ruta válida');
    }

    if (!pedido.fecha) {
      errores.push('Debe especificar una fecha');
    } else {
      const fechaPedido = new Date(pedido.fecha);
      const hoy = new Date();
      hoy.setHours(0, 0, 0, 0);

      if (fechaPedido < hoy) {
        errores.push('La fecha no puede ser anterior a hoy');
      }
    }

    if (!pedido.cantidad || pedido.cantidad <= 0) {
      errores.push('La cantidad debe ser mayor a 0');
    } else if (pedido.cantidad > 1000) {
      errores.push('La cantidad no puede exceder 1000 paquetes por pedido');
    }

    return {
      valido: errores.length === 0,
      errores
    };
  }

  /**
   * Obtiene estadísticas de las operaciones
   */
  obtenerEstadisticas(): Observable<any> {
    return this.http.get(`${this.baseUrl}/simulacion/estadisticas`)
      .pipe(
        catchError(() => of({
          totalPedidos: 0,
          vuelosCompletados: 0,
          paquetesEnTransito: 0,
          eficienciaPromedio: 0
        }))
      );
  }
}
