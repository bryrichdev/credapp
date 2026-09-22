-- ---------------------------------------------------------------------------
-- Starter taxonomy set.
--
-- A working subset of the NUCC Health Care Provider Taxonomy, not the whole
-- code set (~880 codes). Every code below was checked against the NUCC
-- taxonomy PDF at nucc.org and against npiprofile.com rather than typed from
-- memory. Replace this with the published CSV once you have it; the table is
-- keyed on code, so a fuller load is an INSERT ... ON CONFLICT DO NOTHING away.
--
-- grouping holds the NUCC top-level Grouping. specialty holds the name a
-- coordinator would recognise: the Specialization where the code has one,
-- otherwise the Classification.
-- ---------------------------------------------------------------------------

INSERT INTO taxonomies (code, specialty, grouping) VALUES
    -- Family medicine and general practice
    ('207Q00000X', 'Family Medicine',                        'Allopathic & Osteopathic Physicians'),
    ('207QG0300X', 'Geriatric Medicine',                     'Allopathic & Osteopathic Physicians'),
    ('207QA0401X', 'Addiction Medicine',                     'Allopathic & Osteopathic Physicians'),
    ('207QH0002X', 'Hospice and Palliative Medicine',        'Allopathic & Osteopathic Physicians'),
    ('207QS0010X', 'Sports Medicine',                        'Allopathic & Osteopathic Physicians'),
    ('208D00000X', 'General Practice',                       'Allopathic & Osteopathic Physicians'),

    -- Internal medicine and its subspecialties
    ('207R00000X', 'Internal Medicine',                      'Allopathic & Osteopathic Physicians'),
    ('207RC0000X', 'Cardiovascular Disease',                 'Allopathic & Osteopathic Physicians'),
    ('207RG0100X', 'Gastroenterology',                       'Allopathic & Osteopathic Physicians'),
    ('207RE0101X', 'Endocrinology, Diabetes & Metabolism',   'Allopathic & Osteopathic Physicians'),
    ('207RN0300X', 'Nephrology',                             'Allopathic & Osteopathic Physicians'),
    ('207RP1001X', 'Pulmonary Disease',                      'Allopathic & Osteopathic Physicians'),
    ('207RR0500X', 'Rheumatology',                           'Allopathic & Osteopathic Physicians'),
    ('207RI0200X', 'Infectious Disease',                     'Allopathic & Osteopathic Physicians'),
    ('207RH0003X', 'Hematology & Oncology',                  'Allopathic & Osteopathic Physicians'),

    -- Pediatrics
    ('208000000X', 'Pediatrics',                             'Allopathic & Osteopathic Physicians'),
    ('2080N0001X', 'Neonatal-Perinatal Medicine',            'Allopathic & Osteopathic Physicians'),
    ('2080P0202X', 'Pediatric Cardiology',                   'Allopathic & Osteopathic Physicians'),
    ('2080P0204X', 'Pediatric Emergency Medicine',           'Allopathic & Osteopathic Physicians'),

    -- Other primary and hospital-based specialties
    ('207V00000X', 'Obstetrics & Gynecology',                'Allopathic & Osteopathic Physicians'),
    ('207P00000X', 'Emergency Medicine',                     'Allopathic & Osteopathic Physicians'),
    ('207L00000X', 'Anesthesiology',                         'Allopathic & Osteopathic Physicians'),
    ('207N00000X', 'Dermatology',                            'Allopathic & Osteopathic Physicians'),

    -- Psychiatry and neurology
    ('2084P0800X', 'Psychiatry',                             'Allopathic & Osteopathic Physicians'),
    ('2084P0804X', 'Child & Adolescent Psychiatry',          'Allopathic & Osteopathic Physicians'),
    ('2084N0400X', 'Neurology',                              'Allopathic & Osteopathic Physicians'),

    -- Surgical specialties
    ('208600000X', 'Surgery',                                'Allopathic & Osteopathic Physicians'),
    ('2086S0120X', 'Pediatric Surgery',                      'Allopathic & Osteopathic Physicians'),
    ('2086S0122X', 'Plastic and Reconstructive Surgery',     'Allopathic & Osteopathic Physicians'),
    ('2086S0129X', 'Vascular Surgery',                       'Allopathic & Osteopathic Physicians'),
    ('207X00000X', 'Orthopaedic Surgery',                    'Allopathic & Osteopathic Physicians'),
    ('207T00000X', 'Neurological Surgery',                   'Allopathic & Osteopathic Physicians'),
    ('207Y00000X', 'Otolaryngology',                         'Allopathic & Osteopathic Physicians'),
    ('207W00000X', 'Ophthalmology',                          'Allopathic & Osteopathic Physicians'),

    -- Radiology
    ('2085R0202X', 'Diagnostic Radiology',                   'Allopathic & Osteopathic Physicians'),
    ('2085R0001X', 'Radiation Oncology',                     'Allopathic & Osteopathic Physicians'),

    -- Mid-level providers
    ('363A00000X', 'Physician Assistant',                    'Physician Assistants & Advanced Practice Nursing Providers'),
    ('363AM0700X', 'Physician Assistant, Medical',           'Physician Assistants & Advanced Practice Nursing Providers'),
    ('363AS0400X', 'Physician Assistant, Surgical',          'Physician Assistants & Advanced Practice Nursing Providers'),
    ('363L00000X', 'Nurse Practitioner',                     'Physician Assistants & Advanced Practice Nursing Providers'),
    ('363LF0000X', 'Nurse Practitioner, Family',             'Physician Assistants & Advanced Practice Nursing Providers'),

    -- Behavioral health
    ('103T00000X', 'Psychologist',                           'Behavioral Health & Social Service Providers'),
    ('103TC0700X', 'Psychologist, Clinical',                 'Behavioral Health & Social Service Providers'),
    ('104100000X', 'Social Worker',                          'Behavioral Health & Social Service Providers'),
    ('1041C0700X', 'Social Worker, Clinical',                'Behavioral Health & Social Service Providers'),

    -- Rehabilitative
    ('225100000X', 'Physical Therapist',                     'Respiratory, Developmental, Rehabilitative and Restorative Service Providers')
ON CONFLICT (code) DO NOTHING;
