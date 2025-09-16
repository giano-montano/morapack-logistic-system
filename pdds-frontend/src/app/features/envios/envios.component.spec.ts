import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EnviosComponent } from './envios.component';

describe('EnviosComponent', () => {
  let component: EnviosComponent;
  let fixture: ComponentFixture<EnviosComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [EnviosComponent]
    });
    fixture = TestBed.createComponent(EnviosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
