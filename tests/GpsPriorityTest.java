package com.veloster.vehicle;
public final class GpsPriorityTest {
 public static void main(String[] args){
  SpeedArbiter s=new SpeedArbiter();
  s.bcm(48,1000);s.vehicle(49,1000);s.gps(0,1000);
  check(s,1000,0,"GPS");
  s.bcm(55,4900);check(s,5000,0,"GPS");check(s,5001,55,"5W BCM");
  s.gps(43,5100);check(s,5100,43,"GPS");
  s.gps(Float.NaN,5200);check(s,5200,55,"5W BCM");
  s.vehicle(52,5300);s.bcm(Float.NaN,5300);check(s,5300,52,"외부 차속");
  check(s,7301,Float.NaN,"미수신");
  s.gps(10,8000);s.gps(20,7900);check(s,8000,10,"GPS");
  s.clearGps();check(s,8000,Float.NaN,"미수신");
 }
 static void check(SpeedArbiter s,long t,float v,String src){
  if(Float.compare(s.speed(t),v)!=0||!src.equals(s.source(t)))throw new AssertionError(src+" at "+t);
 }
}
