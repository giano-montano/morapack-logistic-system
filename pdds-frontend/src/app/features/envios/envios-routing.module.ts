import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { EnviosComponent } from './envios.component';

const routes: Routes = [{ path: '', component: EnviosComponent }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class EnviosRoutingModule { }
