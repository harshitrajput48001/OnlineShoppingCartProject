INSERT INTO products(name,description,price,stock) SELECT 'Wireless Mouse','2.4 GHz USB wireless mouse',499.00,25 WHERE NOT EXISTS(SELECT 1 FROM products WHERE name='Wireless Mouse');
INSERT INTO products(name,description,price,stock) SELECT 'Keyboard','USB standard keyboard',799.00,20 WHERE NOT EXISTS(SELECT 1 FROM products WHERE name='Keyboard');
INSERT INTO products(name,description,price,stock) SELECT 'Headphones','Wired stereo headphones',999.00,15 WHERE NOT EXISTS(SELECT 1 FROM products WHERE name='Headphones');
