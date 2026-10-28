;; Exports its memory and a table entry that grows it.
(module
  (memory (export "mem") 1)
  (table (export "tab") 1 funcref)

  (func $grow (result i32) (memory.grow (i32.const 1)))

  (elem (i32.const 0) $grow)
)
