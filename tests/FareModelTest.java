package com.veloster.vehicle;
public final class FareModelTest {
 public static void main(String[] args){
  TaxiFare f=new TaxiFare(); f.start(TaxiTariff.of("광주"),0);
  f.add(0,32,0); check(f.total()==4800,"base allowance");
  f.add(36,170,0); check(f.total()==4900,"mixed allowance");
  f.add(0,32,0); check(f.total()==5000,"waiting");
  f.add(Float.NaN,120,0); check(f.total()==5000,"unknown speed");
  f.start(TaxiTariff.of("광주"),0);
  f.add(36,170,0); f.add(7.2f,32,0); check(f.total()==4900,"simultaneous low speed");
 }
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
}
