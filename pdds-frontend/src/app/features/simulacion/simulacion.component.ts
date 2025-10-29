import { Component, OnInit, OnDestroy } from '@angular/core';
import { Pedido, Ruta, SimulacionResponse, SimulacionService, Vuelo } from "./simulacion.service";
import * as L from 'leaflet';

L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'assets/leaflet/marker-icon-2x.png',
  iconUrl: 'assets/leaflet/marker-icon.png',
  shadowUrl: 'assets/leaflet/marker-shadow.png'
});

@Component({
  selector: 'app-simulacion',
  templateUrl: './simulacion.component.html',
  styleUrls: ['./simulacion.component.css']
})
export class SimulacionComponent implements OnInit, OnDestroy {

  rutas: Ruta[] = [];
  pedido: Pedido = { id: 0, rutaId: 0, fecha: '', cantidad: 1 };
  resultadoSimulacion: SimulacionResponse | null = null;

  private map!: L.Map;
  private avionMarkers: L.Marker[] = [];
  private activeIntervals: any[] = [];

  private readonly COORDENADAS: { [key: string]: [number, number] } = {
    'Lima': [-12.0464, -77.0428],
    'Bruselas': [50.8503, 4.3517],
    'Baku': [40.4093, 49.8671],
    'Cusco': [-13.5319, -71.9675],
    'Arequipa': [-16.4090, -71.5375],
    'Puno': [-15.8402, -70.0219]
  };

  constructor(private simulacionService: SimulacionService) { }

  ngOnInit(): void {
    this.cargarRutas();
    this.initMap();
  }

  ngOnDestroy(): void {
    this.clearAllIntervals();
    this.clearMarkers();
    if (this.map) {
      this.map.off();
      this.map.remove();
    }
  }

  cargarRutas() {
    this.simulacionService.getRutas().subscribe(rutas => this.rutas = rutas);
  }

  initMap() {
    if (this.map) {
      this.map.off();
      this.map.remove();
    }

    this.map = L.map('map', {
      center: [20, 0],
      zoom: 2,
      minZoom: 1,
      maxZoom: 19,
      worldCopyJump: false
    });

    L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
      attribution: '© OpenStreetMap © CARTO',
      subdomains: 'abcd',
      maxZoom: 19
    }).addTo(this.map);

    // Marcadores de ejemplo
    const sedes: [number, number][] = [
      [-12.0464, -77.0428],
      [50.8503, 4.3517],
      [40.4093, 49.8671]
    ];

    sedes.forEach(coords => L.marker(coords).addTo(this.map));

    // Ajusta la vista para que todas las sedes se vean
    this.map.fitBounds(L.latLngBounds(sedes).pad(0.2));

    // NO uses setMaxBounds con fitWorld, elimina esto:
    // this.map.setMaxBounds([[-90, -180],[90,180]]);
  }




  private addSedesMarkers() {
    // Definimos un arreglo tipado de sedes
    const sedes: { nombre: string; coords: [number, number] }[] = [
      { nombre: 'Lima, Perú', coords: [-12.0464, -77.0428] },
      { nombre: 'Bruselas, Bélgica', coords: [50.8503, 4.3517] },
      { nombre: 'Baku, Azerbaiyán', coords: [40.4093, 49.8671] }
    ];

    sedes.forEach(sede => {
      const marker = L.marker(sede.coords).addTo(this.map); // coords ahora es reconocido
      marker.bindPopup(`<b>${sede.nombre}</b>`);
    });
  }


  private configurarVistaInicial() {
    const sedesPrincipales = [
      this.COORDENADAS['Lima'],
      this.COORDENADAS['Bruselas'],
      this.COORDENADAS['Baku']
    ];

    const bounds = L.latLngBounds(sedesPrincipales);
    this.map.fitBounds(bounds, { padding: [50, 50] });
  }

  simular() {
    if (!this.pedido.rutaId || !this.pedido.fecha || !this.pedido.cantidad) {
      alert('Por favor complete todos los campos');
      return;
    }

    this.simulacionService.simularPedido(this.pedido).subscribe({
      next: (res) => {
        this.resultadoSimulacion = res;
        if (res.vuelos && res.vuelos.length > 0) {
          this.animarAviones(res.vuelos);
        }
      },
      error: (error) => {
        console.error('Error en simulación:', error);
        alert('Error al simular pedido');
      }
    });
  }

  animarAviones(vuelos: Vuelo[]) {
    this.clearAllIntervals();
    this.clearMarkers();

    vuelos.forEach((vuelo, index) => {
      const origenCoords = this.getCoords(vuelo.origen);
      const destinoCoords = this.getCoords(vuelo.destino);

      if (!origenCoords || !destinoCoords) {
        console.warn(`Coordenadas no encontradas para vuelo: ${vuelo.origen} -> ${vuelo.destino}`);
        return;
      }

      const planeIcon = L.icon({
        iconUrl: 'assets/plane.png',
        iconSize: [32, 32],
        iconAnchor: [16, 16],
        popupAnchor: [0, -16]
      });

      const marker = L.marker(origenCoords, { icon: planeIcon }).addTo(this.map);
      marker.bindPopup(`<b>Vuelo: ${vuelo.codigoVuelo}</b><br>${vuelo.origen} → ${vuelo.destino}<br>Paquetes: ${vuelo.cantidadPaquetes || 'N/A'}`);
      this.avionMarkers.push(marker);

      // Animar con delay entre aviones
      setTimeout(() => {
        this.animarAvion(marker, origenCoords, destinoCoords);
      }, index * 500);
    });
  }

  private animarAvion(marker: L.Marker, origen: [number, number], destino: [number, number]) {
    let step = 0;
    const totalSteps = 60;
    const deltaLat = (destino[0] - origen[0]) / totalSteps;
    const deltaLng = (destino[1] - origen[1]) / totalSteps;

    const intervalId = setInterval(() => {
      if (step >= totalSteps) {
        clearInterval(intervalId);
        marker.setLatLng(destino);
        marker.openPopup();
        const i = this.activeIntervals.indexOf(intervalId);
        if (i > -1) this.activeIntervals.splice(i, 1);
        return;
      }

      const newLat = origen[0] + deltaLat * step;
      const newLng = origen[1] + deltaLng * step;
      marker.setLatLng([newLat, newLng]);
      step++;
    }, 80);

    this.activeIntervals.push(intervalId);
  }

  private clearAllIntervals() {
    this.activeIntervals.forEach(interval => clearInterval(interval));
    this.activeIntervals = [];
  }

  private clearMarkers() {
    this.avionMarkers.forEach(marker => {
      if (this.map && this.map.hasLayer(marker)) {
        this.map.removeLayer(marker);
      }
    });
    this.avionMarkers = [];
  }

  getCoords(ciudad: string): [number, number] | null {
    return this.COORDENADAS[ciudad] || null;
  }

  getCurrentDate(): string {
    return new Date().toISOString().split('T')[0];
  }
}
