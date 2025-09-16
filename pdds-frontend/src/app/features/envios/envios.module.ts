import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { EnviosRoutingModule } from './envios-routing.module';
import { EnviosComponent } from './envios.component';


@NgModule({
  declarations: [
    EnviosComponent
  ],
  imports: [
    CommonModule,
    EnviosRoutingModule
  ]
})
export class EnviosModule { }
