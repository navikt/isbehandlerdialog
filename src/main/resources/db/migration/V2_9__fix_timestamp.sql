UPDATE MELDING SET tidspunkt=tidspunkt + interval '2 hours' WHERE innkommende AND type='FORESPORSEL_PASIENT_LEGEERKLARING';
