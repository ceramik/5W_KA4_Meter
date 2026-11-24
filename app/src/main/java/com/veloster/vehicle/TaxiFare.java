package com.veloster.vehicle;
/** Original fare logic from sung9155/i30-pd-dashboard (MIT); see UPSTREAM.md. */
public final class TaxiFare {
    static final float SLOW_KMH=15;
    private TaxiTariff tariff=TaxiTariff.of("서울");
    private float meters;
    private double timeRemainder;
    public int distUnits,timeUnits,distWon,timeWon;
    private float won;
    public void start(TaxiTariff t,float sur){tariff=t;meters=0;timeRemainder=0;distUnits=0;timeUnits=0;distWon=0;timeWon=0;won=t.base*(1+sur);}
    public void start(float sur){start(tariff,sur);}
    public TaxiTariff tariff(){return tariff;}
    public void add(float kmh,float dtSec,float sur){
        if(dtSec<=0||Float.isNaN(dtSec)||Float.isInfinite(dtSec))return;
        // During an active trip, missing speed is time-only, never fabricated distance.
        boolean slow=Float.isNaN(kmh)||kmh<=SLOW_KMH;
        if(slow){
            timeRemainder+=dtSec;
            while(timeRemainder+0.0001>=tariff.stepS){
                timeRemainder=Math.max(0,timeRemainder-tariff.stepS);
                int w=tariff.stepWon(tariff.baseM+tariff.stepM*(distUnits+timeUnits));
                timeUnits++;timeWon+=w;won+=w*(1+sur);
                meters+=tariff.stepM;
            }
        }else{
            meters+=kmh/3.6f*dtSec;
            while(meters>=tariff.baseM+tariff.stepM*(distUnits+timeUnits+1)){
                int w=tariff.stepWon(tariff.baseM+tariff.stepM*(distUnits+timeUnits));
                distUnits++;distWon+=w;won+=w*(1+sur);
            }
        }
    }
    float savedTimeRemainder(){return (float)timeRemainder;}
    void restoreTimeRemainder(float value){timeRemainder=Math.max(0,Math.min(tariff.stepS,value));}
    float secondsToNext(){return (float)Math.max(0,tariff.stepS-timeRemainder);}
    float timeProgress(){return (float)(timeRemainder/tariff.stepS);}
    int nextStepWon(){return tariff.stepWon(tariff.baseM+tariff.stepM*(distUnits+timeUnits));}
    float savedMeters(){return meters;}
    float savedWon(){return won;}
    void restore(TaxiTariff t,float m,int du,int tu,int dw,int tw,float w){tariff=t;meters=m;timeRemainder=0;distUnits=du;timeUnits=tu;distWon=dw;timeWon=tw;won=w;}
    void copyFrom(TaxiFare o){restore(o.tariff,o.meters,o.distUnits,o.timeUnits,o.distWon,o.timeWon,o.won);timeRemainder=o.timeRemainder;}
    public int total(){return tariff.base+distWon+timeWon+surcharge();}
    public int surcharge(){return Math.round((won-tariff.base-distWon-timeWon)/100f)*100;}
    public float toNext(){return tariff.baseM+tariff.stepM*(distUnits+timeUnits+1)-meters;}
    public float progress(){int n=distUnits+timeUnits;float from=n==0?0:tariff.baseM+tariff.stepM*n,to=tariff.baseM+tariff.stepM*(n+1);return Math.max(0,Math.min(1,(meters-from)/(to-from)));}
}
