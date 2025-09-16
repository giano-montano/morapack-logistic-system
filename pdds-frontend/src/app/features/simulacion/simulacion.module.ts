import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { SimulacionRoutingModule } from './simulacion-routing.module';
import { SimulacionComponent } from './simulacion.component';


@NgModule({
  declarations: [
    SimulacionComponent
  ],
  imports: [
    CommonModule,
    SimulacionRoutingModule
  ]
})
export class SimulacionModule { }
