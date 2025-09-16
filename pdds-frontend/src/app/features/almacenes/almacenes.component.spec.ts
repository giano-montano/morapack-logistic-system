import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AlmacenesComponent } from './almacenes.component';

describe('AlmacenesComponent', () => {
  let component: AlmacenesComponent;
  let fixture: ComponentFixture<AlmacenesComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AlmacenesComponent]
    });
    fixture = TestBed.createComponent(AlmacenesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
