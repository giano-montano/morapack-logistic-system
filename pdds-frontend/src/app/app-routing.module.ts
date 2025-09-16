import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

const routes: Routes = [{ path: 'pedidos', loadChildren: () => import('./features/pedidos/pedidos.module').then(m => m.PedidosModule) }, { path: 'vuelos', loadChildren: () => import('./features/vuelos/vuelos.module').then(m => m.VuelosModule) }, { path: 'almacenes', loadChildren: () => import('./features/almacenes/almacenes.module').then(m => m.AlmacenesModule) }, { path: 'envios', loadChildren: () => import('./features/envios/envios.module').then(m => m.EnviosModule) }, { path: 'simulacion', loadChildren: () => import('./features/simulacion/simulacion.module').then(m => m.SimulacionModule) }];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
