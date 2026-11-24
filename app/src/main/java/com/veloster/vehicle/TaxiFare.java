package com.veloster.vehicle;
/** Original fare logic from sung9155/i30-pd-dashboard (MIT); see UPSTREAM.md. */
public final class TaxiFare {
    static final float SLOW_KMH=15;
    private TaxiTariff tariff=TaxiTariff.of("서울");
    private float meters;
    
    public int distUnits,timeUnits,distWon,timeWon;
    private float won;
    public void start(TaxiTariff t,float sur){tariff=t;meters=0;distUnits=0;timeUnits=0;distWon=0;timeWon=0;won=t.base*(1+sur);}
    public void start(float sur){start(tariff,sur);}
    public TaxiTariff tariff(){return tariff;}
    float limitSpeed(){return "서울".equals(tariff.region)?15.72f:15f;}
    boolean slow(float kmh){return !Float.isNaN(kmh)&&kmh<=limitSpeed();}
    public void add(float kmh,float dtSec,float sur){
        if(Float.isNaN(kmh)||Float.isInfinite(kmh)||kmh<0||dtSec<=0||Float.isNaN(dtSec)||Float.isInfinite(dtSec))return;
        // Partial simultaneous calculation: actual distance always counts;
        // low-speed time contributes equivalent distance to the same fare pulse accumulator.
        double distance=kmh/3.6*dtSec;
        double timed=slow(kmh)?dtSec*tariff.stepM/(double)tariff.stepS:0;
        meters+=(float)(distance+timed);
        while(meters+0.001f>=tariff.baseM+tariff.stepM*(distUnits+timeUnits+1)){
            int w=nextStepWon();
            if(timed>=distance&&timed>0){timeUnits++;timeWon+=w;}else{distUnits++;distWon+=w;}
            won+=w*(1+sur);
        }
    }
    float savedTimeRemainder(){return 0;}
    void restoreTimeRemainder(float value){/* legacy independent timer is not carried into a new model */}
    float secondsToNext(){return toNext()*tariff.stepS/tariff.stepM;}
    float timeProgress(){return progress();}
    int nextStepWon(){return tariff.stepWon(tariff.baseM+tariff.stepM*(distUnits+timeUnits));}
    float secondsToNext(float kmh){return (float)(toNext()/(kmh/3.6+tariff.stepM/(double)tariff.stepS));}
    float savedMeters(){return meters;}
    float savedWon(){return won;}
    void restore(TaxiTariff t,float m,int du,int tu,int dw,int tw,float w){tariff=t;meters=m;distUnits=du;timeUnits=tu;distWon=dw;timeWon=tw;won=w;}
    void copyFrom(TaxiFare o){restore(o.tariff,o.meters,o.distUnits,o.timeUnits,o.distWon,o.timeWon,o.won);}
    public int total(){return tariff.base+distWon+timeWon+surcharge();}
    public int surcharge(){return Math.round((won-tariff.base-distWon-timeWon)/100f)*100;}
    public float toNext(){return tariff.baseM+tariff.stepM*(distUnits+timeUnits+1)-meters;}
    public float progress(){int n=distUnits+timeUnits;float from=n==0?0:tariff.baseM+tariff.stepM*n,to=tariff.baseM+tariff.stepM*(n+1);return Math.max(0,Math.min(1,(meters-from)/(to-from)));}
}
