UPDATE MELDING
SET tekst = '[Teksten er fjernet]',
    document = CASE
                   WHEN innkommende THEN '[]'::jsonb
                   ELSE '[{"key": null, "type": "PARAGRAPH", "texts": ["[Teksten er fjernet]"], "title": null}]'::jsonb
        END
WHERE uuid IN (
    '3388549a-179e-4a97-a0f5-fc046f0419c7'
    );