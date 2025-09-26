import {Component, OnInit} from '@angular/core';
import {Pedido, Ruta, SimulacionResponse, SimulacionService, Vuelo} from "./simulacion.service";
import {interval} from "rxjs";
import * as L from 'leaflet';

interface AvionAnimado {
  codigoVuelo: string;
  origen: string;
  destino: string;
  posX: number;
  posY: number;
  destX: number;
  destY: number;
}

@Component({
  selector: 'app-simulacion',
  templateUrl: './simulacion.component.html',
  styleUrls: ['./simulacion.component.css']
})
export class SimulacionComponent implements OnInit {

  rutas: Ruta[] = [];
  pedido: Pedido = { id: 0, rutaId: 0, fecha: '', cantidad: 1 };
  resultadoSimulacion: SimulacionResponse | null = null;

  private map!: L.Map;
  private avionMarkers: L.Marker[] = [];

  constructor(private simulacionService: SimulacionService) { }

  ngOnInit(): void {
    this.cargarRutas();
    this.initMap();
  }

  cargarRutas() {
    this.simulacionService.getRutas().subscribe(rutas => this.rutas = rutas);
  }

  initMap() {
    this.map = L.map('map', {
      center: [0, 0],
      zoom: 2,
      maxZoom: 6
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: 'Map data © OpenStreetMap contributors',
      detectRetina: true
    }).addTo(this.map);

    // marcadores de sedes MoraPack
    L.marker([-12.0464, -77.0428]).addTo(this.map).bindPopup('Lima, Perú');
    L.marker([50.8503, 4.3517]).addTo(this.map).bindPopup('Bruselas, Bélgica');
    L.marker([40.4093, 49.8671]).addTo(this.map).bindPopup('Baku, Azerbaiyán');
  }


  simular() {
    this.simulacionService.simularPedido(this.pedido).subscribe(res => {
      this.resultadoSimulacion = res;
      this.animarAviones(res.vuelos);
    });
  }

  animarAviones(vuelos: Vuelo[]) {
    // limpiar marcadores anteriores
    this.avionMarkers.forEach(marker => this.map.removeLayer(marker));
    this.avionMarkers = [];

    vuelos.forEach(vuelo => {
      const origenCoords = this.getCoords(vuelo.origen);
      const destinoCoords = this.getCoords(vuelo.destino);

      const marker = L.marker(origenCoords, { icon: L.icon({ iconUrl: 'assets/plane.png', iconSize: [48, 48] }) }).addTo(this.map);
      this.avionMarkers.push(marker);

      // animación simple lineal
      let step = 0;
      const steps = 100;
      const deltaLat = (destinoCoords[0] - origenCoords[0]) / steps;
      const deltaLng = (destinoCoords[1] - origenCoords[1]) / steps;

      const interval = setInterval(() => {
        if (step >= steps) {
          clearInterval(interval);
          marker.setLatLng(destinoCoords);
          return;
        }
        marker.setLatLng([origenCoords[0] + deltaLat * step, origenCoords[1] + deltaLng * step]);
        step++;
      }, 50);
    });
  }

  getCoords(ciudad: string): [number, number] {
    switch(ciudad) {
      case 'Lima': return [-12.0464, -77.0428];
      case 'Bruselas': return [50.8503, 4.3517];
      case 'Baku': return [40.4093, 49.8671];
      case 'Cusco': return [-13.5319, -71.9675];
      case 'Arequipa': return [-16.4090, -71.5375];
      case 'Puno': return [-15.8402, -70.0219];
      default: return [0, 0];
    }
  }
}
