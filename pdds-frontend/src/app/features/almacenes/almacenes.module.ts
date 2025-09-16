import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { AlmacenesRoutingModule } from './almacenes-routing.module';
import { AlmacenesComponent } from './almacenes.component';


@NgModule({
  declarations: [
    AlmacenesComponent
  ],
  imports: [
    CommonModule,
    AlmacenesRoutingModule
  ]
})
export class AlmacenesModule { }
