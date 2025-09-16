import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VuelosComponent } from './vuelos.component';

describe('VuelosComponent', () => {
  let component: VuelosComponent;
  let fixture: ComponentFixture<VuelosComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [VuelosComponent]
    });
    fixture = TestBed.createComponent(VuelosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
