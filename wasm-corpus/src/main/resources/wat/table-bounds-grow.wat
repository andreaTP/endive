;; Grows a table with no maximum and one with a large maximum.
(module
  (table $unbounded 1 funcref)
  (table $bounded 1 5000000 funcref)

  (func (export "growUnbounded") (param i32) (result i32)
    (table.grow $unbounded (ref.null func) (local.get 0)))

  (func (export "growBounded") (param i32) (result i32)
    (table.grow $bounded (ref.null func) (local.get 0)))
)
