import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SimulacionComponent } from './simulacion.component';

describe('SimulacionComponent', () => {
  let component: SimulacionComponent;
  let fixture: ComponentFixture<SimulacionComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [SimulacionComponent]
    });
    fixture = TestBed.createComponent(SimulacionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
