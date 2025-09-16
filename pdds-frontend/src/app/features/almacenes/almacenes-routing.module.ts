import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AlmacenesComponent } from './almacenes.component';

const routes: Routes = [{ path: '', component: AlmacenesComponent }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class AlmacenesRoutingModule { }
