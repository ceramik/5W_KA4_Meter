package com.veloster.vehicle;
public final class SpeedArbiterTest {
  static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
  public static void main(String[] args){
    SpeedArbiter s=new SpeedArbiter();
    check(Float.isNaN(s.speed(1000)),"missing");
    s.bcm(50,1000);s.vehicle(60,1000);s.gps(45,1000);
    check(s.speed(1000)==45&&s.source(1000).equals("GPS"),"GPS first");
    s.gps(0,1100);check(s.speed(1100)==0,"valid GPS zero");
    s.bcm(51,5200);s.vehicle(61,5200);
    check(s.speed(5200)==51&&s.source(5200).equals("5W BCM"),"GPS timeout fallback");
    s.gps(48,5300);check(s.speed(5300)==48,"GPS recovery");
    s.clearGps();check(s.speed(5300)==51,"GPS clear");
    s.vehicle(62,7301);check(s.speed(7301)==62,"external fallback");
    check(Float.isNaN(s.speed(9302)),"all expired");
    System.out.println("SpeedArbiter tests passed");
  }
}
