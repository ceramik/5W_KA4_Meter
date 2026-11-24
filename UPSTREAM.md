# Upstream attribution

- Original repository: https://github.com/sung9155/i30-pd-dashboard
- `shared/src/com/veloster/vehicle/TaxiCluster.java`: original source of ten `STRIDE` LED horse frames and visual/behavioral references for horse animation, audio panel and receipt panel
- `shared/src/com/veloster/vehicle/TaxiFare.java`: copied / adapted taxi fare calculation logic
- `shared/src/com/veloster/vehicle/TaxiTariff.java`: copied / adapted 17-region tariff data and methods
- Original repository license appears to be MIT; check the current upstream LICENSE before redistribution.
- This project reimplements drawing methods in a standalone Canvas View; not an exact verbatim copy of the entire `TaxiCluster.java`.

- `TaxiZones.java` and `TaxiZoneData.java`: imported from upstream unchanged. Boundaries: Statistics Korea SGIS (https://sgis.kostat.go.kr, KOGL Type 1), modified by vuski/admdongkor (https://github.com/vuski/admdongkor, CC BY 4.0), simplified to 200 m by upstream. See https://creativecommons.org/licenses/by/4.0/ and https://www.kogl.or.kr/info/licenseType1.do.
