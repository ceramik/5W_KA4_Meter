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
    public void add(float kmh,float dtSec,float sur){
        if(Float.isNaN(kmh)||dtSec<=0)return;
        boolean slow=kmh<=SLOW_KMH;
        meters+=slow?dtSec/tariff.stepS*tariff.stepM:kmh/3.6f*dtSec;
        while(meters>=tariff.baseM+tariff.stepM*(distUnits+timeUnits+1)){
            int w=tariff.stepWon(tariff.baseM+tariff.stepM*(distUnits+timeUnits));
            if(slow){timeUnits++;timeWon+=w;}else{distUnits++;distWon+=w;}
            won+=w*(1+sur);
        }
    }
    public int total(){return tariff.base+distWon+timeWon+surcharge();}
    public int surcharge(){return Math.round((won-tariff.base-distWon-timeWon)/100f)*100;}
    public float toNext(){return tariff.baseM+tariff.stepM*(distUnits+timeUnits+1)-meters;}
    public float progress(){int n=distUnits+timeUnits;float from=n==0?0:tariff.baseM+tariff.stepM*n,to=tariff.baseM+tariff.stepM*(n+1);return Math.max(0,Math.min(1,(meters-from)/(to-from)));}
}
