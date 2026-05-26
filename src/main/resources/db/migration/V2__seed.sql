INSERT INTO author (id, name, country) VALUES
    (1, 'Jane Smith',        'US'),
    (2, 'John Williams',     'US'),
    (3, 'Olga Petrova',      'RU'),
    (4, 'Hiroshi Tanaka',    'JP'),
    (5, 'Marie Dubois',      'FR');

INSERT INTO book (id, author_id, title, price, published_at) VALUES
    (1, 1, 'The Quiet Algorithm',    19.99, '2023-04-10'),
    (2, 1, 'Distributed by Default', 32.50, '2024-01-15'),
    (3, 2, 'SQL Without Tears',      24.00, '2022-11-01'),
    (4, 2, 'Streams in Production',  41.75, '2025-03-20'),
    (5, 3, 'Tundra Engineering',     17.10, '2023-09-05'),
    (6, 4, 'Kaizen for Code',        28.40, '2024-06-30'),
    (7, 4, 'Quiet Refactors',        22.00, '2025-02-14'),
    (8, 5, 'Le Cache Intransigent',  35.00, '2024-10-10');

INSERT INTO review (book_id, rating, text) VALUES
    (1, 5, 'Outstanding clarity.'),
    (1, 4, 'Good, but the last chapter drags.'),
    (1, 5, 'Read it twice.'),
    (2, 3, 'Decent intro to the topic.'),
    (3, 5, 'Best SQL book I have read in years.'),
    (3, 4, 'Solid, recommended for juniors.'),
    (4, 2, 'Repetitive.'),
    (5, 5, 'A delightful surprise.'),
    (6, 4, 'Practical and short.'),
    (7, 5, 'Outstanding.'),
    (7, 5, 'Buy a copy for every junior.'),
    (8, 3, 'Heavy on theory.');
