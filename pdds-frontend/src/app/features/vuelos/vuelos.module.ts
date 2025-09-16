import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { VuelosRoutingModule } from './vuelos-routing.module';
import { VuelosComponent } from './vuelos.component';


@NgModule({
  declarations: [
    VuelosComponent
  ],
  imports: [
    CommonModule,
    VuelosRoutingModule
  ]
})
export class VuelosModule { }
