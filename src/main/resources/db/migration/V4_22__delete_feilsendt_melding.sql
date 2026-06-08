UPDATE MELDING
SET tekst = '[Teksten er fjernet]',
    document = CASE
       WHEN innkommende THEN '[]'::jsonb
       ELSE '[{"key": null, "type": "PARAGRAPH", "texts": ["[Teksten er fjernet]"], "title": null}]'::jsonb
    END
WHERE uuid IN (
   '2d110f94-885d-4bf7-a8e8-ec7ead941208',
   '1685134e-2d5f-4744-9bbe-e9c5163f989b'
);