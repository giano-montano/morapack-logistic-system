import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { SimulacionRoutingModule } from './simulacion-routing.module';
import { SimulacionComponent } from './simulacion.component';
import {FormsModule} from "@angular/forms";
import { HttpClientModule } from '@angular/common/http';

@NgModule({
  declarations: [
    SimulacionComponent
  ],
  imports: [
    CommonModule,
    SimulacionRoutingModule,
    HttpClientModule,
    FormsModule
  ]
})
export class SimulacionModule { }
