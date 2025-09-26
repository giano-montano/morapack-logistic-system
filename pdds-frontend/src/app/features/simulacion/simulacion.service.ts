import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {Observable, of} from 'rxjs';
import { environment } from 'src/environments/environment';
// ===========================
// INTERFACES / MODELOS
// ===========================
export interface Ruta {
  id: number;
  origen: string;
  destino: string;
  duracion: number; // minutos
}

export interface Pedido {
  id: number;
  rutaId: number;
  fecha: string;
  cantidad: number;
}

export interface Vuelo {
  id: number;
  rutaId: number;
  codigoVuelo: string;
  asientosDisponibles: number;
  origen: string;
  destino: string;
}


export interface SimulacionResponse {
  rutas: Ruta[];
  pedidos: Pedido[];
  vuelos: Vuelo[];
  totalCosto: number;
}
@Injectable({
  providedIn: 'root'
})
export class SimulacionService {

  private readonly baseUrl = environment.apiUrl;

  //constructor(private http: HttpClient) {
  //}

  // Obtener todas las rutas disponibles
  /*getRutas(): Observable<Ruta[]> {
    return this.http.get<Ruta[]>(`${this.baseUrl}/rutas`);
  }

  // Simular un pedido
  simularPedido(pedido: Pedido): Observable<SimulacionResponse> {
    return this.http.post<SimulacionResponse>(`${this.baseUrl}/simular`, pedido);
  }*/
  // constructor(private http: HttpClient) {} // descomenta cuando uses API real
  constructor() {}

  // Hardcode para rutas
  getRutas(): Observable<Ruta[]> {
    const mockRutas: Ruta[] = [
      { id: 1, origen: 'Lima', destino: 'Cusco', duracion: 120 },
      { id: 2, origen: 'Arequipa', destino: 'Puno', duracion: 90 }
    ];
    return of(mockRutas);
  }

  // Simulación hardcodeada de pedido
  simularPedido(pedido: Pedido): Observable<SimulacionResponse> {
    const rutaSeleccionada = { id: pedido.rutaId, origen: 'Lima', destino: 'Cusco', duracion: 120 };
    const vuelosSimulados: Vuelo[] = [
      { id: 1, rutaId: pedido.rutaId, codigoVuelo: 'LA101', asientosDisponibles: 5, origen: 'Lima', destino: 'Cusco' },
      { id: 2, rutaId: pedido.rutaId, codigoVuelo: 'LA102', asientosDisponibles: 3, origen: 'Lima', destino: 'Cusco' }
    ];


    const totalCosto = pedido.cantidad * 50; // precio ficticio

    const response: SimulacionResponse = {
      rutas: [rutaSeleccionada],
      pedidos: [pedido],
      vuelos: vuelosSimulados,
      totalCosto
    };

    return of(response);
  }
}
